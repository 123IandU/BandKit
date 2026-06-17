pub mod protocol;
pub mod auth;
pub mod device;
pub mod tools;
pub mod crypto_aesccm;
pub mod crypto_aesctr;

pub use device::{DeviceSession, SessionManager, ConnectType, DeviceConfig, DeviceEvent};
pub use auth::{AuthState, build_auth_step_1_bytes, build_auth_step_2};

use parking_lot::Mutex;
use std::sync::OnceLock;

static GLOBAL_SESSION_MANAGER: OnceLock<Mutex<SessionManager>> = OnceLock::new();

pub fn session_manager() -> &'static Mutex<SessionManager> {
    GLOBAL_SESSION_MANAGER.get_or_init(|| Mutex::new(SessionManager::new()))
}

#[cfg(feature = "android-jni")]
pub mod jni;
