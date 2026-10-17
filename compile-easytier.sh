#!/bin/bash
set -o errexit
set -o pipefail
set -o nounset
# Set magic variables for current file & dir
__dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
__file="${__dir}/$(basename "${BASH_SOURCE[0]}")"
__base="$(basename ${__file} .sh)"
if [[ -z "$NDK_HOME" && -z "$ANDROID_NDK_HOME" && -z "$ANDROID_NDK_ROOT" ]]; then
  echo "Android NDK: NDK_HOME not found. please set env \$NDK_HOME"
  exit 1
fi
: "${NDK_HOME:=${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT}}}"
TMPDIR=$(mktemp -d)
clear_tmp () {
  rm -rf $TMPDIR
}
trap 'echo -e "Aborted, error $? in command: $BASH_COMMAND"; trap ERR; clear_tmp; exit 1' ERR INT

ABIS="armeabi-v7a arm64-v8a x86 x86_64"

declare -A TARGET_MAP
TARGET_MAP["armeabi-v7a"]="armv7-linux-androideabi"
TARGET_MAP["arm64-v8a"]="aarch64-linux-android"
TARGET_MAP["x86"]="i686-linux-android"
TARGET_MAP["x86_64"]="x86_64-linux-android"

for abi in $ABIS; do
  rust_target="${TARGET_MAP[$abi]}"
  if ! rustup target list --installed | grep -q "$rust_target"; then
    rustup target add "$rust_target"
  fi
done

if ! command -v cargo-ndk &> /dev/null && ! cargo ndk --version &> /dev/null; then
  cargo install cargo-ndk
fi

# The EasyTier library is loaded in-process by
# com.v2ray.ang.service.EasyTierManager for the no-tun SOCKS5 dataplane.
mkdir -p "$__dir/V2rayNG/app/libs"
for abi in $ABIS; do
  cargo ndk -t "$abi" build --release -p easytier-jni --manifest-path "$__dir/easytier-jni/Cargo.toml" --target-dir "$TMPDIR/target"
  rust_target="${TARGET_MAP[$abi]}"
  mkdir -p "$__dir/V2rayNG/app/libs/$abi"
  cp "$TMPDIR/target/$rust_target/release/libeasytier.so" "$__dir/V2rayNG/app/libs/$abi/"
done

rm -rf $TMPDIR
