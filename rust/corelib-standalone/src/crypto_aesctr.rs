use aes::{Aes128, cipher::{KeyIvInit, StreamCipher}};
use ctr::Ctr128BE;

type Aes128Ctr = Ctr128BE<Aes128>;

pub fn aes128_ctr_crypt(key: &[u8; 16], iv: &[u8; 16], data: &[u8]) -> Vec<u8> {
    let mut buffer = data.to_vec();
    let mut cipher = Aes128Ctr::new(key.into(), iv.into());
    cipher.apply_keystream(&mut buffer);
    buffer
}
