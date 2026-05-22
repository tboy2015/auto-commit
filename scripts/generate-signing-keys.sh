#!/usr/bin/env bash
#
# 生成 JetBrains Marketplace 插件签名所需的 RSA 自签证书与私钥。
# 一次性运行。输出到 secrets/ 目录（已在 .gitignore）。
#
# 参考：https://plugins.jetbrains.com/docs/intellij/plugin-signing.html
#
set -euo pipefail

OUT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/secrets"
mkdir -p "$OUT_DIR"
cd "$OUT_DIR"

if [[ -f chain.crt || -f private.pem ]]; then
  echo "❌ secrets/chain.crt 或 secrets/private.pem 已存在。"
  echo "   重新生成会作废已发布版本的签名链。如确定覆盖请先手动备份并删除。"
  exit 1
fi

read -r -p "证书 Common Name (你的名字或组织, 比如 'AI Commit Team'): " CN
if [[ -z "$CN" ]]; then
  echo "CN 不能为空"; exit 1
fi

# 提示输入并确认密码
while : ; do
  read -r -s -p "为私钥设置一个密码（之后 PRIVATE_KEY_PASSWORD 环境变量用这个）: " PW1; echo
  read -r -s -p "再输一遍确认: " PW2; echo
  [[ "$PW1" == "$PW2" && -n "$PW1" ]] && break
  echo "两次输入不一致或为空，请重试。"
done

echo "🔐 生成 RSA 2048 私钥（加密保护）..."
openssl genpkey \
  -algorithm RSA \
  -pkeyopt rsa_keygen_bits:2048 \
  -aes256 \
  -pass "pass:$PW1" \
  -out private.pem

echo "📜 生成自签证书（10 年有效）..."
openssl req -x509 -new -nodes -days 3650 \
  -key private.pem \
  -passin "pass:$PW1" \
  -subj "/CN=$CN" \
  -out chain.crt

chmod 600 private.pem chain.crt

cat <<EOF

✅ 完成。文件：
   $OUT_DIR/private.pem  (加密私钥)
   $OUT_DIR/chain.crt    (公开证书)

🔒 重要：
  - 这两个文件已自动被 .gitignore 忽略，永远不要提交到 git
  - 异地备份它们 + 密码。丢失意味着失去对当前 plugin id 的签名权
  - 把它们配置成环境变量后即可签名/发布：

    export CERTIFICATE_CHAIN="\$(cat $OUT_DIR/chain.crt)"
    export PRIVATE_KEY="\$(cat $OUT_DIR/private.pem)"
    export PRIVATE_KEY_PASSWORD="<刚才设的密码>"

  - GitHub Actions：把上面三个值粘到 repo Settings → Secrets and variables → Actions

下一步：
    ./gradlew signPlugin   # 本地试签
    ./gradlew publishPlugin # 真发布（还需要 PUBLISH_TOKEN）
EOF
