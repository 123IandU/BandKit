use jni::JNIEnv;
use jni::objects::{JClass, JString, JByteArray};
use jni::sys::{jbyteArray, jobject, jlong};

use crate::{session_manager, ConnectType, DeviceConfig};
use crate::auth::build_auth_step_2;
use crate::device::DeviceEvent;
use pb::xiaomi::protocol;
use prost::Message;

#[unsafe(no_mangle)]
pub extern "C" fn Java_com_bandburg_core_NativeLib_nativeInit(
    _env: JNIEnv,
    _class: JClass,
) {
    let _ = session_manager();
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_com_bandburg_core_NativeLib_nativeCreateSession(
    mut env: JNIEnv,
    _class: JClass,
    name: JString,
    addr: JString,
    authkey: JString,
    connect_type: jlong,
) -> jlong {
    let name: String = env.get_string(&name).unwrap().into();
    let addr: String = env.get_string(&addr).unwrap().into();
    let authkey: String = env.get_string(&authkey).unwrap().into();
    let ct = match connect_type {
        1 => ConnectType::BLE,
        _ => ConnectType::SPP,
    };

    let session = session_manager().lock().create_session(
        name, addr, authkey, ct, DeviceConfig::default(),
    );

    Box::into_raw(Box::new(session)) as jlong
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn Java_com_bandburg_core_NativeLib_nativeDestroySession(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    unsafe {
        let session = Box::from_raw(handle as *mut std::sync::Arc<parking_lot::Mutex<crate::device::DeviceSession>>);
        let addr = session.lock().addr.clone();
        session_manager().lock().remove_session(&addr);
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_com_bandburg_core_NativeLib_nativeBuildSppHello(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jbyteArray {
    let session = unsafe { &*(handle as *const std::sync::Arc<parking_lot::Mutex<crate::device::DeviceSession>>) };
    let hello = session.lock().build_spp_hello();
    let bytes = env.byte_array_from_slice(&hello).unwrap();
    bytes.into_raw()
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_com_bandburg_core_NativeLib_nativeBuildL1StartReq(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jbyteArray {
    let session = unsafe { &*(handle as *const std::sync::Arc<parking_lot::Mutex<crate::device::DeviceSession>>) };
    let pkt = session.lock().build_l1start_req();
    let bytes = env.byte_array_from_slice(&pkt).unwrap();
    bytes.into_raw()
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_com_bandburg_core_NativeLib_nativeBuildAuthStep1(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jbyteArray {
    let session = unsafe { &*(handle as *const std::sync::Arc<parking_lot::Mutex<crate::device::DeviceSession>>) };
    let pkt = crate::auth::build_auth_step_1_bytes(&mut session.lock().auth);
    let bytes = env.byte_array_from_slice(&pkt).unwrap();
    bytes.into_raw()
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_com_bandburg_core_NativeLib_nativeProcessData(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    data: jbyteArray,
) -> jobject {
    let session = unsafe { &*(handle as *const std::sync::Arc<parking_lot::Mutex<crate::device::DeviceSession>>) };
    let data = unsafe { env.convert_byte_array(JByteArray::from_raw(data)).unwrap() };

    let events = {
        let mut dev = session.lock();
        dev.on_data_received(&data)
    };

    let mut results = Vec::new();
    for event in events {
        match event {
            DeviceEvent::WearPacket(wp) => {
                results.push(wp);
            }
        }
    }

    let mut results_json = String::from("[");
    for (i, wp) in results.iter().enumerate() {
        if i > 0 { results_json.push(','); }
        results_json.push_str(&serde_json::to_string(wp).unwrap_or_default());
    }
    results_json.push(']');

    let j_str = env.new_string(&results_json).unwrap();
    j_str.into_raw()
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_com_bandburg_core_NativeLib_nativeHandleAuthStep2(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    device_verify_json: JString,
    force_android: bool,
) -> jbyteArray {
    let session = unsafe { &*(handle as *const std::sync::Arc<parking_lot::Mutex<crate::device::DeviceSession>>) };
    let json_str: String = env.get_string(&device_verify_json).unwrap().into();

    let device_verify: protocol::auth::DeviceVerify = match serde_json::from_str(&json_str) {
        Ok(v) => v,
        Err(e) => {
            env.throw_new("java/lang/RuntimeException", format!("Invalid JSON: {}", e)).unwrap();
            return std::ptr::null_mut();
        }
    };

    let mut dev = session.lock();
    let is_ble = dev.connect_type == ConnectType::BLE;
    match build_auth_step_2(&mut dev.auth, &device_verify, force_android, is_ble) {
        Ok(wp) => {
            dev.ensure_l2_cipher();
            let pkt_bytes = dev.build_l2_packet(wp);
            let bytes = env.byte_array_from_slice(&pkt_bytes).unwrap();
            bytes.into_raw()
        }
        Err(e) => {
            env.throw_new("java/lang/RuntimeException", e).unwrap();
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_com_bandburg_core_NativeLib_nativeSendProtobuf(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    pb_data: jbyteArray,
) -> jbyteArray {
    let session = unsafe { &*(handle as *const std::sync::Arc<parking_lot::Mutex<crate::device::DeviceSession>>) };
    let data = unsafe { env.convert_byte_array(JByteArray::from_raw(pb_data)).unwrap() };

    match protocol::WearPacket::decode(std::io::Cursor::new(&data)) {
        Ok(wp) => {
            let dev = session.lock();
            let l2_bytes = dev.build_l2_packet(wp);
            let bytes = env.byte_array_from_slice(&l2_bytes).unwrap();
            bytes.into_raw()
        }
        Err(e) => {
            env.throw_new("java/lang/RuntimeException", format!("Invalid protobuf: {}", e)).unwrap();
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_com_bandburg_core_NativeLib_nativeGetSessionCount(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    session_manager().lock().session_addrs().len() as jlong
}
