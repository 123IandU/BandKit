use std::fmt;
use prost::Message;

use super::layer1::{L1DataType, L1Packet};
use pb::xiaomi::protocol::WearPacket;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum L2Channel {
    Pb = 1,
    Mass = 2,
    MassVoice = 3,
    FileSensor = 4,
    FileFitness = 5,
    Ota = 6,
    Network = 7,
    Lyra = 8,
    Research = 9,
    MultiModal = 10,
}

impl TryFrom<u8> for L2Channel {
    type Error = L2Error;
    fn try_from(v: u8) -> Result<Self, Self::Error> {
        use L2Channel::*;
        Ok(match v {
            1 => Pb,
            2 => Mass,
            3 => MassVoice,
            4 => FileSensor,
            5 => FileFitness,
            6 => Ota,
            7 => Network,
            8 => Lyra,
            9 => Research,
            10 => MultiModal,
            _ => return Err(L2Error::InvalidChannel(v)),
        })
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum L2OpCode {
    Write = 1,
    WriteEnc = 2,
    Read = 3,
}

impl TryFrom<u8> for L2OpCode {
    type Error = L2Error;
    fn try_from(v: u8) -> Result<Self, Self::Error> {
        use L2OpCode::*;
        Ok(match v {
            1 => Write,
            2 => WriteEnc,
            3 => Read,
            _ => return Err(L2Error::InvalidOpCode(v)),
        })
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum L2Error {
    TooShort,
    InvalidChannel(u8),
    InvalidOpCode(u8),
    LengthMismatch { expected: usize, actual: usize },
    DecryptFailed,
}

impl fmt::Display for L2Error {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            L2Error::TooShort => write!(f, "Packet too short"),
            L2Error::InvalidChannel(ch) => write!(f, "Invalid channel: {}", ch),
            L2Error::InvalidOpCode(op) => write!(f, "Invalid opcode: {}", op),
            L2Error::LengthMismatch { expected, actual } => {
                write!(f, "Length mismatch: expected {}, actual {}", expected, actual)
            }
            L2Error::DecryptFailed => write!(f, "Decryption failed"),
        }
    }
}

#[derive(Debug, Clone)]
pub struct L2Packet {
    pub channel: L2Channel,
    pub opcode: L2OpCode,
    pub payload: Vec<u8>,
}

impl L2Packet {
    pub fn new(channel: L2Channel, opcode: L2OpCode, payload: Vec<u8>) -> Self {
        Self { channel, opcode, payload }
    }

    pub fn to_bytes(&self) -> Vec<u8> {
        let mut out = Vec::with_capacity(2 + self.payload.len());
        out.push(self.channel as u8);
        out.push(self.opcode as u8);
        out.extend_from_slice(&self.payload);
        out
    }

    pub fn from_bytes(buf: &[u8], cipher: Option<&dyn L2Cipher>) -> Result<Self, L2Error> {
        if buf.len() < 2 { return Err(L2Error::TooShort); }
        let ch = L2Channel::try_from(buf[0])?;
        let op = L2OpCode::try_from(buf[1])?;
        let body = &buf[2..];
        let payload = match (op, cipher) {
            (L2OpCode::WriteEnc, Some(c)) => c.decrypt(body).map_err(|_| L2Error::DecryptFailed)?,
            _ => body.to_vec(),
        };
        Ok(Self { channel: ch, opcode: op, payload })
    }

    pub fn pb_write(packet: WearPacket) -> Self {
        Self::new(L2Channel::Pb, L2OpCode::Write, packet.encode_to_vec())
    }

    pub fn pb_write_enc(packet: WearPacket, cipher: &dyn L2Cipher) -> Result<Self, L2Error> {
        let ct = cipher.encrypt(&packet.encode_to_vec()).map_err(|_| L2Error::DecryptFailed)?;
        Ok(Self::new(L2Channel::Pb, L2OpCode::WriteEnc, ct))
    }

    pub fn into_l1(self, seq: u8, frx: bool) -> L1Packet {
        L1Packet::new(L1DataType::Data, frx, seq, self.to_bytes())
    }

    pub fn from_l1(l1: &L1Packet, cipher: Option<&dyn L2Cipher>) -> Result<Self, L2Error> {
        match l1.pkt_type {
            L1DataType::Data => L2Packet::from_bytes(&l1.payload, cipher),
            _ => Err(L2Error::InvalidOpCode(0)),
        }
    }
}

pub trait L2Cipher {
    fn encrypt(&self, plaintext: &[u8]) -> Result<Vec<u8>, ()>;
    fn decrypt(&self, ciphertext: &[u8]) -> Result<Vec<u8>, ()>;
}
