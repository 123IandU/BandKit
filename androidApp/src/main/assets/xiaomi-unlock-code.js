// Xiaomi Unlock Code Calculator
// Based on https://github.com/leset0ng/ab-unlockcode
// Runs in BandKit ScriptRunner

sandbox.gui({
    title: '小米设备解锁码计算器',
    elements: [
        { type: 'input', id: 'mac', label: 'MAC 地址', placeholder: '例: 00:11:22:33:44:55' },
        { type: 'input', id: 'sn', label: '序列号 (SN)', placeholder: '例: SN123456789' },
        {
            type: 'select', id: 'algorithm', label: '算法版本',
            options: [
                { value: 'old', label: '旧算法 (MAC + SN + XIAOMI)' },
                { value: 'new', label: '新算法 (SN + MAC + XIAOMI) — S5/10P 及更新设备' }
            ]
        },
        { type: 'button', id: 'calc', text: '计算解锁码' },
        { type: 'label', id: 'result', text: '请输入 MAC 和 SN 后点击计算' },
        { type: 'button', id: 'copy', text: '复制解锁码' }
    ]
});

var ctrl = sandbox.activeGUI;

function normalizeMac(mac) {
    return mac.toUpperCase()
        .replace(/\uff1a/g, '')  // Chinese full-width colon
        .replace(/:/g, '')
        .replace(/-/g, '')
        .replace(/ /g, '')
        .replace(/\./g, '');
}

function normalizeSn(sn) {
    return sn.toUpperCase().trim();
}

async function sha256(message) {
    var encoder = new TextEncoder();
    var data = encoder.encode(message);
    var hashBuffer = await crypto.subtle.digest('SHA-256', data);
    var hashArray = Array.from(new Uint8Array(hashBuffer));
    return hashArray;
}

async function calcUnlockCode(mac, sn, isNew) {
    var m = normalizeMac(mac);
    var s = normalizeSn(sn);
    var input;
    if (isNew) {
        input = s + m + 'XIAOMI';
    } else {
        input = m + s + 'XIAOMI';
    }
    var hash = await sha256(input);
    var code = '';
    for (var i = 0; i < 10; i++) {
        code += (hash[i] % 10).toString();
    }
    return code;
}

ctrl.on('button:click', 'calc', async function() {
    var mac = ctrl.getValue('mac');
    var sn = ctrl.getValue('sn');
    var algo = ctrl.getValue('algorithm');

    if (!mac || !sn) {
        ctrl.setValue('result', '请填写 MAC 地址和序列号');
        return;
    }

    try {
        var code = await calcUnlockCode(mac, sn, algo === 'new');
        ctrl.setValue('result', '解锁码: ' + code);
        sandbox.log('计算完成 — 解锁码: ' + code);
    } catch (e) {
        ctrl.setValue('result', '计算失败: ' + e.message);
        sandbox.error('计算失败: ' + e);
    }
});

ctrl.on('button:click', 'copy', function() {
    var result = ctrl.getValue('result');
    var match = result.match(/解锁码:\s*(\d+)/);
    if (match) {
        // Use fallback since Clipboard API may not be available
        var ta = document.createElement('textarea');
        ta.value = match[1];
        document.body.appendChild(ta);
        ta.select();
        document.execCommand('copy');
        document.body.removeChild(ta);
        sandbox.log('已复制解锁码: ' + match[1]);
    } else {
        sandbox.warn('没有可复制的解锁码');
    }
});
