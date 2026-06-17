use hmac::{Hmac, Mac};
use sha2::Sha256;
use pb::xiaomi::protocol;
use prost::Message;

use crate::crypto_aesccm::aes128_ccm_encrypt;
use crate::protocol::layer2::L2Packet;
use crate::tools;

type HmacSha256 = Hmac<Sha256>;

#[derive(Clone)]
pub struct AuthState {
    pub authkey: String,
    pub is_authed: bool,
    pub random_bytes: Vec<u8>,
    pub enc_key: Vec<u8>,
    pub dec_key: Vec<u8>,
    pub enc_nonce: Vec<u8>,
    pub dec_nonce: Vec<u8>,
}

impl AuthState {
    pub fn new(authkey: String) -> Self {
        Self {
            authkey,
            is_authed: false,
            random_bytes: vec![],
            enc_key: vec![],
            dec_key: vec![],
            enc_nonce: vec![],
            dec_nonce: vec![],
        }
    }
}

pub fn build_auth_step_1(nonce: &[u8]) -> protocol::WearPacket {
    let account_payload = protocol::auth::AppVerify {
        app_random: nonce.to_vec(),
        app_device_id: None,
        check_dynamic_code: None,
    };
    let pkt_payload = protocol::Account {
        payload: Some(protocol::account::Payload::AuthAppVerify(account_payload)),
    };
    protocol::WearPacket {
        r#type: protocol::wear_packet::Type::Account as i32,
        id: protocol::account::AccountId::AuthVerify as u32,
        payload: Some(protocol::wear_packet::Payload::Account(pkt_payload)),
    }
}

pub fn build_auth_step_2(
    auth_state: &mut AuthState,
    device_verify: &protocol::auth::DeviceVerify,
    force_android: bool,
    connect_type_is_ble: bool,
) -> Result<protocol::WearPacket, String> {
    let w_random = &device_verify.device_random;
    let w_sign = &device_verify.device_sign;

    if w_random.len() != 16 || w_sign.len() != 32 {
        return Err("nonce/hmac length mismatch".to_string());
    }

    let authkey = &auth_state.authkey;
    let p_random_vec = &auth_state.random_bytes;
    if p_random_vec.len() != 16 {
        return Err("phone nonce length mismatch".to_string());
    }

    let p_random_arr: &[u8; 16] = p_random_vec[..].try_into().unwrap();
    let w_random_arr: &[u8; 16] = w_random[..].try_into().unwrap();

    let key_bytes = string_to_u8_16(authkey)
        .ok_or_else(|| "invalid authkey hex len".to_string())?;

    let block64 = kdf_miwear(&key_bytes, p_random_arr, w_random_arr);

    let dec_key: Vec<u8> = block64[0..16].to_vec();
    let enc_key: Vec<u8> = block64[16..32].to_vec();
    let dec_nonce: Vec<u8> = block64[32..36].to_vec();
    let enc_nonce: Vec<u8> = block64[36..40].to_vec();

    let mut mac = HmacSha256::new_from_slice(&dec_key).unwrap();
    mac.update(w_random);
    mac.update(p_random_vec);
    let expect = mac.finalize().into_bytes();
    if w_sign.as_slice() != &expect[..] {
        return Err("Auth HMAC mismatch, This usually means your AuthKey is wrong.".to_string());
    }

    let mut mac2 = HmacSha256::new_from_slice(&enc_key).unwrap();
    mac2.update(p_random_vec);
    mac2.update(w_random);
    let encrypted_signs = mac2.finalize().into_bytes().to_vec();

    let mut device_type = protocol::companion_device::DeviceType::Android as i32;
    if connect_type_is_ble && !force_android {
        device_type = protocol::companion_device::DeviceType::Ios as i32;
    }

    let proto_companion_device = protocol::CompanionDevice {
        device_type,
        system_version: None,
        device_name: "BandBurg".to_string(),
        app_capability: Some(0xffff_ffff),
        region: None,
        server_prefix: None,
    };
    let companion_device = proto_companion_device.encode_to_vec();

    let mut pkt_nonce = Vec::with_capacity(12);
    pkt_nonce.extend_from_slice(&enc_nonce);
    pkt_nonce.extend_from_slice(&0u32.to_le_bytes());
    pkt_nonce.extend_from_slice(&0u32.to_le_bytes());

    let enc_key_arr: &[u8; 16] = (&enc_key[..]).try_into().unwrap();
    let nonce_arr: &[u8; 12] = (&pkt_nonce[..]).try_into().unwrap();
    let encrypted_device_info = aes128_ccm_encrypt(enc_key_arr, nonce_arr, &[], &companion_device);

    let account_payload = protocol::auth::AppConfirm {
        app_sign: encrypted_signs,
        encrypt_companion_device: encrypted_device_info,
    };
    let pkt_payload = protocol::Account {
        payload: Some(protocol::account::Payload::AuthAppConfirm(account_payload)),
    };

    auth_state.enc_key = enc_key;
    auth_state.dec_key = dec_key;
    auth_state.enc_nonce = enc_nonce;
    auth_state.dec_nonce = dec_nonce;

    Ok(protocol::WearPacket {
        r#type: protocol::wear_packet::Type::Account as i32,
        id: protocol::account::AccountId::AuthConfirm as u32,
        payload: Some(protocol::wear_packet::Payload::Account(pkt_payload)),
    })
}

pub fn build_auth_step_1_bytes(auth_state: &mut AuthState) -> Vec<u8> {
    let nonce = tools::generate_random_bytes(16);
    auth_state.random_bytes = nonce.clone();
    let pkt = build_auth_step_1(&nonce);
    L2Packet::pb_write(pkt).to_bytes()
}

fn string_to_u8_16(s: &String) -> Option<[u8; 16]> {
    if s.len() != 32 { return None; }
    let mut result = [0u8; 16];
    for i in 0..16 {
        let byte_str = &s[i * 2..i * 2 + 2];
        match u8::from_str_radix(byte_str, 16) {
            Ok(val) => result[i] = val,
            Err(_) => return None,
        }
    }
    Some(result)
}

fn kdf_miwear(secret_key: &[u8; 16], phone_nonce: &[u8; 16], watch_nonce: &[u8; 16]) -> [u8; 64] {
    let mut init_key = [0u8; 32];
    init_key[..16].copy_from_slice(phone_nonce);
    init_key[16..].copy_from_slice(watch_nonce);

    let mut mac = HmacSha256::new_from_slice(&init_key).expect("HMAC key length fixed");
    mac.update(secret_key);
    let hmac_key = mac.finalize().into_bytes();

    let mut okm = [0u8; 64];
    let tag = b"miwear-auth";
    let mut offset = 0;
    let mut prev: Vec<u8> = Vec::new();
    for counter in 1u8..=3 {
        let mut mac = HmacSha256::new_from_slice(&hmac_key).unwrap();
        mac.update(&prev);
        mac.update(tag);
        mac.update(&[counter]);
        prev = mac.finalize().into_bytes().to_vec();
        let end = (offset + 32).min(64);
        okm[offset..end].copy_from_slice(&prev[..end - offset]);
        offset = end;
    }
    okm
}
