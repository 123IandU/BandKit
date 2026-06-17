use aes::Aes128;
use ccm::{
    Ccm, KeyInit,
    aead::{Aead, Key, Nonce, Payload},
    consts::{U4, U12},
};

type Aes128Ccm = Ccm<Aes128, U4, U12>;

pub fn aes128_ccm_encrypt(
    key: &[u8; 16],
    nonce: &[u8; 12],
    aad: &[u8],
    plaintext: &[u8],
) -> Vec<u8> {
    let mut key_buf = Key::<Aes128Ccm>::default();
    key_buf.copy_from_slice(key);
    let cipher = Aes128Ccm::new(&key_buf);
    let mut nonce_buf = Nonce::<Aes128Ccm>::default();
    nonce_buf.copy_from_slice(nonce);
    cipher.encrypt(&nonce_buf, Payload { msg: plaintext, aad }).expect("CCM encryption failed")
}

pub fn aes128_ccm_decrypt(
    key: &[u8; 16],
    nonce: &[u8; 12],
    aad: &[u8],
    ciphertext_and_tag: &[u8],
) -> Result<Vec<u8>, ccm::aead::Error> {
    let mut key_buf = Key::<Aes128Ccm>::default();
    key_buf.copy_from_slice(key);
    let cipher = Aes128Ccm::new(&key_buf);
    let mut nonce_buf = Nonce::<Aes128Ccm>::default();
    nonce_buf.copy_from_slice(nonce);
    cipher.decrypt(&nonce_buf, Payload { msg: ciphertext_and_tag, aad })
}
