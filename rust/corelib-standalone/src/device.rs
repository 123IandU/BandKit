use std::collections::HashMap;
use std::sync::Arc;
use parking_lot::Mutex;
use pb::xiaomi::protocol;
use prost::Message;

use crate::auth::AuthState;
use crate::protocol::layer1::L1Packet;
use crate::protocol::layer1cmd::{CmdCode, L1CmdBuilder, L1CmdPacket};
use crate::protocol::layer2::{L2Channel, L2Cipher, L2Packet};
use crate::crypto_aesctr::aes128_ctr_crypt;
use crate::tools;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ConnectType {
    SPP = 0,
    BLE = 1,
}

#[derive(Debug, Clone)]
pub struct DeviceConfig {
    pub chunk_size_spp: usize,
    pub chunk_size_ble: usize,
    pub tx_win_overrun_allowance: u8,
}

impl Default for DeviceConfig {
    fn default() -> Self {
        Self {
            chunk_size_spp: 666,
            chunk_size_ble: 244,
            tx_win_overrun_allowance: 0,
        }
    }
}

pub struct V2L2Cipher {
    enc_key: Vec<u8>,
    dec_key: Vec<u8>,
}

impl V2L2Cipher {
    pub fn new(enc_key: Vec<u8>, dec_key: Vec<u8>) -> Self {
        Self { enc_key, dec_key }
    }
}

impl L2Cipher for V2L2Cipher {
    fn encrypt(&self, plaintext: &[u8]) -> Result<Vec<u8>, ()> {
        Ok(aes128_ctr_crypt(
            &tools::vec_to_array_16_opt(&self.enc_key).unwrap(),
            &tools::vec_to_array_16_opt(&self.enc_key).unwrap(),
            plaintext,
        ))
    }

    fn decrypt(&self, ciphertext: &[u8]) -> Result<Vec<u8>, ()> {
        Ok(aes128_ctr_crypt(
            &tools::vec_to_array_16_opt(&self.dec_key).unwrap(),
            &tools::vec_to_array_16_opt(&self.dec_key).unwrap(),
            ciphertext,
        ))
    }
}

pub struct DeviceSession {
    pub name: String,
    pub addr: String,
    pub connect_type: ConnectType,
    pub config: DeviceConfig,
    pub auth: AuthState,
    pub l2_cipher: Option<V2L2Cipher>,
    pub recv_buffer: Vec<u8>,
    pub rx_expect_seq: u8,
    pub cmd_exchanged: bool,
    pub tx_win: u8,
    pub send_timeout_ms: u64,
    pub pending_wear_packet_callback: Option<Box<dyn Fn(protocol::WearPacket) + Send + Sync>>,
}

impl DeviceSession {
    pub fn new(
        name: String,
        addr: String,
        authkey: String,
        connect_type: ConnectType,
        config: DeviceConfig,
    ) -> Self {
        Self {
            name,
            addr,
            connect_type,
            config,
            auth: AuthState::new(authkey),
            l2_cipher: None,
            recv_buffer: Vec::new(),
            rx_expect_seq: 0,
            cmd_exchanged: false,
            tx_win: 16,
            send_timeout_ms: 10_000,
            pending_wear_packet_callback: None,
        }
    }

    pub fn build_l1start_req(&self) -> Vec<u8> {
        let start_req = L1CmdBuilder::new()
            .cmd(CmdCode::CmdL1startReq)
            .version(1, 0, 0)
            .mps(64512)
            .tx_win(32)
            .send_timeout(10_000)
            .build()
            .unwrap();
        let pkt = L1Packet::new(
            crate::protocol::layer1::L1DataType::Cmd,
            false,
            0,
            start_req.to_payload_bytes(),
        );
        pkt.to_bytes()
    }

    pub fn build_spp_hello(&self) -> Vec<u8> {
        if self.connect_type == ConnectType::SPP {
            tools::hex_stream_to_bytes("badcfe00c00300000100ef").unwrap()
        } else {
            vec![]
        }
    }

    pub fn on_data_received(&mut self, data: &[u8]) -> Vec<DeviceEvent> {
        self.recv_buffer.extend_from_slice(data);
        let mut events = Vec::new();

        let mut idx = 0usize;
        while idx + 8 <= self.recv_buffer.len() {
            if !(self.recv_buffer[idx] == 0xa5 && self.recv_buffer[idx + 1] == 0xa5) {
                idx = idx.saturating_add(1);
                continue;
            }
            let declared_len = u16::from_le_bytes([
                self.recv_buffer[idx + 4],
                self.recv_buffer[idx + 5],
            ]) as usize;
            let total = 8 + declared_len;
            if idx + total > self.recv_buffer.len() {
                break;
            }
            let frame = self.recv_buffer[idx..idx + total].to_vec();
            idx += total;

            if let Ok(l1) = L1Packet::from_bytes(&frame) {
                match l1.pkt_type {
                    crate::protocol::layer1::L1DataType::Ack => {}
                    crate::protocol::layer1::L1DataType::Nak => {}
                    crate::protocol::layer1::L1DataType::Cmd => {
                        if let Some(cmd) = L1CmdPacket::from_payload_bytes(&l1.payload) {
                            if cmd.cmd == CmdCode::CmdL1startRsp {
                                self.cmd_exchanged = true;
                                if let Some(win) = cmd.get_tx_win() {
                                    self.tx_win = win.clamp(1, 255) as u8;
                                }
                                if let Some(to) = cmd.get_send_timeout() {
                                    self.send_timeout_ms = to as u64;
                                }
                            }
                        }
                    }
                    crate::protocol::layer1::L1DataType::Data => {
                        if l1.seq == self.rx_expect_seq {
                            self.rx_expect_seq = self.rx_expect_seq.wrapping_add(1);
                            let cipher_ref = self.l2_cipher.as_ref().map(|c| c as &dyn L2Cipher);
                            if let Ok(l2p) = L2Packet::from_l1(&l1, cipher_ref) {
                                if l2p.channel == L2Channel::Pb {
                                    if let Ok(wp) =
                                        protocol::WearPacket::decode(std::io::Cursor::new(&l2p.payload))
                                    {
                                        events.push(DeviceEvent::WearPacket(wp));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if idx > 0 {
            self.recv_buffer.drain(0..idx);
        }
        if self.recv_buffer.is_empty() {
            self.recv_buffer.clear();
        }

        events
    }

    pub fn build_l2_packet(&self, packet: protocol::WearPacket) -> Vec<u8> {
        if let Some(ref cipher) = self.l2_cipher {
            L2Packet::pb_write_enc(packet.clone(), cipher)
                .unwrap_or_else(|_| L2Packet::pb_write(packet))
                .to_bytes()
        } else {
            L2Packet::pb_write(packet).to_bytes()
        }
    }

    pub fn ensure_l2_cipher(&mut self) {
        if self.l2_cipher.is_some() {
            return;
        }
        if self.auth.enc_key.len() == 16 && self.auth.dec_key.len() == 16 {
            self.l2_cipher = Some(V2L2Cipher::new(
                self.auth.enc_key.clone(),
                self.auth.dec_key.clone(),
            ));
        }
    }
}

#[derive(Debug)]
pub enum DeviceEvent {
    WearPacket(protocol::WearPacket),
}

pub struct SessionManager {
    sessions: HashMap<String, Arc<Mutex<DeviceSession>>>,
}

impl SessionManager {
    pub fn new() -> Self {
        Self { sessions: HashMap::new() }
    }

    pub fn create_session(
        &mut self,
        name: String,
        addr: String,
        authkey: String,
        connect_type: ConnectType,
        config: DeviceConfig,
    ) -> Arc<Mutex<DeviceSession>> {
        let session = Arc::new(Mutex::new(DeviceSession::new(
            name, addr, authkey, connect_type, config,
        )));
        self.sessions.insert(
            session.lock().addr.clone(),
            Arc::clone(&session),
        );
        session
    }

    pub fn get_session(&self, addr: &str) -> Option<Arc<Mutex<DeviceSession>>> {
        self.sessions.get(addr).cloned()
    }

    pub fn remove_session(&mut self, addr: &str) {
        self.sessions.remove(addr);
    }

    pub fn session_addrs(&self) -> Vec<String> {
        self.sessions.keys().cloned().collect()
    }
}
