#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "=========================================================="
echo "  JTrac NG (v3.0.0-beta) Portable Package Builder"
echo "=========================================================="

JETTY_ZIP_OVERRIDE="${1:-}"
JETTY_DIR="$DIR/tools/jetty"
WAR_FILE="$DIR/target/jtrac.war"
OUTPUT_DIR="$DIR/target"
OUTPUT_ZIP="$OUTPUT_DIR/jtrac-ng-3.0.0-beta-portable.zip"
STAGING_ROOT="$OUTPUT_DIR/portable-staging"
BUNDLE_DIR="$STAGING_ROOT/jtrac-ng-3.0.0-beta-portable"

mkdir -p "$JETTY_DIR"

# Helper: Sort X.Y.Z versions semantically and output the highest (portable across Linux, Alpine & macOS)
pick_highest_semver() {
    awk -F. 'NF==3 && $1~/^[0-9]+$/ && $2~/^[0-9]+$/ && $3~/^[0-9]+$/ {
        printf "%05d%05d%05d %s\n", $1, $2, $3, $0
    }' | sort -rn | head -n 1 | awk '{print $2}'
}

find_latest_local_zip() {
    local search_dir="$1"
    if [ ! -d "$search_dir" ]; then
        return 0
    fi
    local best_ver
    best_ver=$(find "$search_dir" -name 'jetty-home-*.zip' -size +1024k 2>/dev/null \
        | sed -n 's|.*/jetty-home-\([0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*\)\.zip$|\1|p' \
        | pick_highest_semver)
    if [ -n "$best_ver" ]; then
        find "$search_dir" -name "jetty-home-${best_ver}.zip" -size +1024k 2>/dev/null | head -n 1
    fi
}

# 1. Resolve Base Jetty ZIP (Explicit Override -> Online Latest 12.1.x -> Local Latest Fallback)
JETTY_ZIP=""
if [ -n "$JETTY_ZIP_OVERRIDE" ]; then
    if [ ! -f "$JETTY_ZIP_OVERRIDE" ]; then
        echo "[ERROR] 指定的 Jetty ZIP 不存在: $JETTY_ZIP_OVERRIDE"
        exit 1
    fi
    JETTY_ZIP="$JETTY_ZIP_OVERRIDE"
    echo "[1/5] 使用手動指定的 Jetty ZIP: $JETTY_ZIP"
else
    echo "[1/5] 正在連線檢查最新穩定版 Jetty 12.1.x..."
    METADATA_URL="https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-home/maven-metadata.xml"
    METADATA_XML=""
    if command -v curl >/dev/null 2>&1; then
        METADATA_XML=$(curl -fsSL --connect-timeout 5 --max-time 8 "$METADATA_URL" 2>/dev/null || true)
    elif command -v wget >/dev/null 2>&1; then
        METADATA_XML=$(wget -qO- --timeout=8 "$METADATA_URL" 2>/dev/null || true)
    fi

    REMOTE_VER=""
    if [ -n "$METADATA_XML" ]; then
        REMOTE_VER=$(printf "%s\n" "$METADATA_XML" \
            | sed -n 's|.*<version>\(12\.1\.[0-9][0-9]*\)</version>.*|\1|p' \
            | pick_highest_semver)
    fi

    if [ -n "$REMOTE_VER" ]; then
        TARGET_ZIP="$JETTY_DIR/jetty-home-${REMOTE_VER}.zip"
        M2_ZIP="$HOME/.m2/repository/org/eclipse/jetty/jetty-home/${REMOTE_VER}/jetty-home-${REMOTE_VER}.zip"
        if [ -f "$TARGET_ZIP" ] && [ -s "$TARGET_ZIP" ]; then
            JETTY_ZIP="$TARGET_ZIP"
            echo "       本地 Jetty 已是最新穩定版 (v${REMOTE_VER}): $JETTY_ZIP"
        elif [ -f "$M2_ZIP" ] && [ -s "$M2_ZIP" ]; then
            cp "$M2_ZIP" "$TARGET_ZIP"
            JETTY_ZIP="$TARGET_ZIP"
            echo "       已從本地 Maven 快取複製最新版 Jetty v${REMOTE_VER} 至 $JETTY_ZIP"
        else
            echo "       偵測到最新穩定版 Jetty v${REMOTE_VER}，正在下載..."
            ARTIFACT_URL="https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-home/${REMOTE_VER}/jetty-home-${REMOTE_VER}.zip"
            TMP_ZIP="${TARGET_ZIP}.tmp"
            rm -f "$TMP_ZIP"
            DL_OK=0
            if command -v curl >/dev/null 2>&1; then
                curl -fL --connect-timeout 8 --max-time 180 -o "$TMP_ZIP" "$ARTIFACT_URL" && DL_OK=1 || DL_OK=0
            elif command -v wget >/dev/null 2>&1; then
                wget -q --timeout=30 -O "$TMP_ZIP" "$ARTIFACT_URL" && DL_OK=1 || DL_OK=0
            fi

            if [ "$DL_OK" -eq 1 ] && [ -s "$TMP_ZIP" ]; then
                # Optional SHA-1 verification
                EXPECTED_SHA1=""
                if command -v curl >/dev/null 2>&1; then
                    EXPECTED_SHA1=$(curl -fsSL --connect-timeout 5 --max-time 8 "${ARTIFACT_URL}.sha1" 2>/dev/null | tr -d ' \r\n' | cut -c1-40 || true)
                fi
                ACTUAL_SHA1=""
                if [ -n "$EXPECTED_SHA1" ]; then
                    if command -v sha1sum >/dev/null 2>&1; then
                        ACTUAL_SHA1=$(sha1sum "$TMP_ZIP" | awk '{print $1}')
                    elif command -v shasum >/dev/null 2>&1; then
                        ACTUAL_SHA1=$(shasum -a 1 "$TMP_ZIP" | awk '{print $1}')
                    fi
                fi
                if [ -n "$EXPECTED_SHA1" ] && [ -n "$ACTUAL_SHA1" ] && [ "$(printf "%s" "$EXPECTED_SHA1" | tr 'A-F' 'a-f')" != "$(printf "%s" "$ACTUAL_SHA1" | tr 'A-F' 'a-f')" ]; then
                    echo "       [WARN] SHA-1 校驗不符，捨棄下載暫存檔。"
                    rm -f "$TMP_ZIP"
                else
                    mv "$TMP_ZIP" "$TARGET_ZIP"
                    JETTY_ZIP="$TARGET_ZIP"
                    echo "       已成功下載並驗證 Jetty v${REMOTE_VER} -> $JETTY_ZIP"
                fi
            else
                rm -f "$TMP_ZIP"
                echo "       [WARN] 下載 Jetty v${REMOTE_VER} 失敗，準備降級使用本地快取..."
            fi
        fi
    else
        echo "       [離線/逾時] 無法連線取得遠端版本資訊，改用本地最新快取..."
    fi

    if [ -z "$JETTY_ZIP" ]; then
        LOCAL_BEST=$(find_latest_local_zip "$JETTY_DIR")
        if [ -n "$LOCAL_BEST" ]; then
            JETTY_ZIP="$LOCAL_BEST"
            echo "       使用本地最新 Jetty ZIP: $JETTY_ZIP"
        else
            M2_BEST=$(find_latest_local_zip "$HOME/.m2/repository/org/eclipse/jetty/jetty-home")
            if [ -n "$M2_BEST" ]; then
                CACHED_DEST="$JETTY_DIR/$(basename "$M2_BEST")"
                cp "$M2_BEST" "$CACHED_DEST"
                JETTY_ZIP="$CACHED_DEST"
                echo "       使用本地 .m2 最新 Jetty ZIP: $JETTY_ZIP"
            else
                echo "[ERROR] 無法連線下載且本地 $JETTY_DIR 無任何可用的 jetty-home-*.zip！"
                exit 1
            fi
        fi
    fi
fi

# 2. Check WAR
if [ ! -f "$WAR_FILE" ]; then
    echo "[2/5] 找不到 $WAR_FILE，正在執行 Maven 編譯..."
    mvn clean package -DskipTests
    if [ ! -f "$WAR_FILE" ]; then
        echo "[ERROR] Maven 編譯完成但未產出 $WAR_FILE！"
        exit 1
    fi
else
    echo "[2/5] 使用現有 WAR: $WAR_FILE"
fi

# 3. Assemble
rm -rf "$STAGING_ROOT"
mkdir -p "$BUNDLE_DIR"

echo "[3/5] 解壓縮基底 Jetty 12 伺服器..."
TEMP_JETTY="$STAGING_ROOT/temp-jetty"
mkdir -p "$TEMP_JETTY"
unzip -q "$JETTY_ZIP" -d "$TEMP_JETTY"
EXTRACTED_HOME=$(find "$TEMP_JETTY" -mindepth 1 -maxdepth 1 -type d | head -n 1)
mv "$EXTRACTED_HOME" "$BUNDLE_DIR/jetty-home"
rm -rf "$TEMP_JETTY"

echo "[4/5] 初始化 Jetty 模組與佈署應用程式..."
(
    cd "$BUNDLE_DIR"
    JAVA_BIN="java"
    if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        JAVA_BIN="$JAVA_HOME/bin/java"
    fi
    "$JAVA_BIN" -jar "jetty-home/start.jar" --create-startd --add-modules=ee10-deploy,http >/dev/null 2>&1
    if [ ! -d "$BUNDLE_DIR/start.d" ]; then
        echo "[ERROR] 初始化 Jetty start.d 失敗（請確認 JAVA_HOME 指向 Java 21+）！"
        exit 1
    fi
)

mkdir -p "$BUNDLE_DIR/webapps"
cp "$WAR_FILE" "$BUNDLE_DIR/webapps/ROOT.war"
mkdir -p "$BUNDLE_DIR/data"

# Copy runtime scripts and README from templates
if [ -d "$DIR/scripts/templates" ]; then
    cp "$DIR/scripts/templates/"* "$BUNDLE_DIR/"
    chmod +x "$BUNDLE_DIR/start.sh" "$BUNDLE_DIR/stop.sh" 2>/dev/null || true
fi

# 5. Compress
echo "[5/5] 正在壓縮可攜版 ZIP..."
mkdir -p "$OUTPUT_DIR"
rm -f "$OUTPUT_ZIP"
(
    cd "$BUNDLE_DIR"
    zip -q -r "$OUTPUT_ZIP" .
)

# Also sync to unzipped directory in output directory for immediate local use
UNZIPPED_TARGET="$OUTPUT_DIR/jtrac-ng-3.0.0-beta-portable"
rm -rf "$UNZIPPED_TARGET"
mkdir -p "$UNZIPPED_TARGET"
cp -r "$BUNDLE_DIR"/* "$UNZIPPED_TARGET"/

rm -rf "$STAGING_ROOT"

echo "=========================================================="
echo "  [成功] 綠色可攜版打包完成！"
echo "  輸出檔案: $OUTPUT_ZIP"
echo "=========================================================="
