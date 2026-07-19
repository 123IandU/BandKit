// 在线脚本商店
sandbox.log("=== 在线脚本商店 ===");

var INDEX_URL = "https://bandburgscript.02studio.xyz/scripts.json";
var SCRIPT_STORE_ID = "__script_store__";

async function fetchScriptList() {
    sandbox.log("正在获取脚本列表...");
    var resp = await fetch(INDEX_URL);
    if (!resp.ok) throw new Error("HTTP " + resp.status);
    return await resp.json();
}

async function fetchScriptCode(url) {
    var resp = await fetch(url);
    if (!resp.ok) throw new Error("HTTP " + resp.status);
    return await resp.text();
}

var gui = sandbox.gui({
    title: "在线脚本商店",
    elements: [
        { type: "label", text: "点击「刷新」获取最新脚本列表" },
        { type: "label", id: "status", text: "就绪", style: "color:var(--text-muted);font-size:12px;" },
        { type: "button", id: "refresh", text: "刷新列表" },
    ]
});

var scriptEntries = [];

gui.on("button:click", "refresh", async function() {
    try {
        gui.setValue("status", "加载中...");
        scriptEntries = await fetchScriptList();
        gui.setValue("status", "共 " + scriptEntries.length + " 个脚本");

        // 移除旧列表
        var oldList = document.getElementById(SCRIPT_STORE_ID);
        if (oldList) oldList.remove();

        // 创建列表容器
        var listContainer = document.createElement("div");
        listContainer.id = SCRIPT_STORE_ID;
        listContainer.style.cssText =
            "margin-top:8px;border:1px solid var(--border);border-radius:6px;overflow:hidden;" +
            "background:var(--bg);color:var(--text);font-size:13px;";

        // 表头
        var header = document.createElement("div");
        header.style.cssText =
            "display:flex;align-items:center;padding:6px 8px;" +
            "background:color-mix(in srgb, var(--bg) 95%, var(--text));" +
            "color:var(--text-muted);font-size:11px;font-weight:bold;border-bottom:1px solid var(--border);";
        var hIdx = document.createElement("span");
        hIdx.style.cssText = "width:28px;flex-shrink:0;text-align:center;";
        hIdx.textContent = "#";
        var hName = document.createElement("span");
        hName.style.cssText = "flex:1;min-width:0;padding:0 4px;";
        hName.textContent = "名称";
        var hAuthor = document.createElement("span");
        hAuthor.style.cssText = "width:80px;flex-shrink:0;padding:0 4px;";
        hAuthor.textContent = "作者";
        var hAction = document.createElement("span");
        hAction.style.cssText = "width:56px;flex-shrink:0;text-align:center;";
        hAction.textContent = "操作";
        header.appendChild(hIdx);
        header.appendChild(hName);
        header.appendChild(hAuthor);
        header.appendChild(hAction);
        listContainer.appendChild(header);

        // 列表行
        scriptEntries.forEach(function(entry, i) {
            var row = document.createElement("div");
            row.style.cssText =
                "display:flex;align-items:center;padding:8px;" +
                "border-bottom:1px solid var(--border);" +
                (i % 2 === 1 ? "background:color-mix(in srgb, var(--bg) 97%, var(--text));" : "");
            if (i === scriptEntries.length - 1) {
                row.style.borderBottom = "none";
            }

            var idx = document.createElement("span");
            idx.style.cssText = "width:28px;flex-shrink:0;text-align:center;color:var(--text-muted);font-size:11px;";
            idx.textContent = (i + 1) + ".";

            var name = document.createElement("span");
            name.style.cssText = "flex:1;min-width:0;padding:0 4px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;";
            name.textContent = entry.name;

            var author = document.createElement("span");
            author.style.cssText = "width:80px;flex-shrink:0;padding:0 4px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:var(--text-muted);font-size:12px;";
            author.textContent = entry.author;

            var action = document.createElement("span");
            action.style.cssText = "width:56px;flex-shrink:0;text-align:center;";

            var dlBtn = document.createElement("button");
            dlBtn.textContent = "下载";
            dlBtn.style.cssText =
                "padding:4px 10px;border:none;border-radius:4px;" +
                "background:var(--button-bg);color:var(--button-text);font-size:12px;cursor:pointer;";
            dlBtn.onclick = async function() {
                try {
                    dlBtn.textContent = "...";
                    dlBtn.disabled = true;
                    dlBtn.style.background = "#666";
                    var code = await fetchScriptCode(entry.url);
                    var ok = sandbox.saveScript(entry.name, code);
                    if (ok) {
                        sandbox.log("已保存: " + entry.name);
                        dlBtn.textContent = "✓";
                        dlBtn.style.background = "#2a7d2a";
                    } else {
                        sandbox.log("已存在: " + entry.name);
                        dlBtn.textContent = "已存";
                        dlBtn.style.background = "#666";
                    }
                } catch (e) {
                    sandbox.log("下载失败: " + entry.name + " - " + e.message);
                    dlBtn.textContent = "失败";
                    dlBtn.style.background = "#e04040";
                    dlBtn.disabled = false;
                }
            };

            action.appendChild(dlBtn);
            row.appendChild(idx);
            row.appendChild(name);
            row.appendChild(author);
            row.appendChild(action);
            listContainer.appendChild(row);
        });

        // 全部下载按钮栏
        var allBar = document.createElement("div");
        allBar.style.cssText =
            "display:flex;justify-content:center;padding:8px;" +
            "border-top:1px solid var(--border);";

        var allBtn = document.createElement("button");
        allBtn.textContent = "全部下载";
        allBtn.style.cssText =
            "padding:6px 16px;border:none;border-radius:6px;" +
            "background:#e04040;color:var(--button-text);font-size:13px;cursor:pointer;";
        allBtn.onclick = async function() {
            allBtn.disabled = true;
            allBtn.textContent = "下载中...";
            allBtn.style.background = "#666";
            for (var j = 0; j < scriptEntries.length; j++) {
                var e = scriptEntries[j];
                try {
                    var c = await fetchScriptCode(e.url);
                    sandbox.saveScript(e.name, c);
                    sandbox.log("(" + (j+1) + "/" + scriptEntries.length + ") " + e.name);
                } catch (ex) {}
            }
            allBtn.textContent = "完成 ✓";
            allBtn.style.background = "#2a7d2a";
            sandbox.log("全部下载完成，请返回脚本页查看");
        };
        allBar.appendChild(allBtn);
        listContainer.appendChild(allBar);

        document.body.appendChild(listContainer);
    } catch (e) {
        gui.setValue("status", "加载失败: " + e.message);
        sandbox.log("获取列表失败: " + e.message);
    }
});

sandbox.log("请点击「刷新列表」开始");
