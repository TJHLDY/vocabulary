"""将 CET-6 JSON 词库导入 MySQL"""
import json, sys, os

# 你的 MySQL 连接信息，按需修改
DB_CONFIG = {
    "host": "localhost",
    "port": 3306,
    "user": "root",
    "password": os.environ.get("DB_PASSWORD", "123456"),
    "database": "vocabulary",
    "charset": "utf8mb4",
}

JSON_URL = "https://raw.githubusercontent.com/KyleBing/english-vocabulary/master/json/4-CET6-%E9%A1%BA%E5%BA%8F.json"

def fetch_json():
    import urllib.request
    print("下载 CET-6 词库 JSON ...")
    req = urllib.request.Request(JSON_URL)
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode("utf-8"))

def import_to_db(words):
    import pymysql
    conn = pymysql.connect(**DB_CONFIG)
    cur = conn.cursor()
    inserted = 0
    for item in words:
        word = item["word"].strip().lower()
        translations = item.get("translations", [])
        if not translations:
            continue
        t = translations[0]
        pos = t.get("type", "").strip()
        meaning = t.get("translation", "").strip()
        if not word or not meaning:
            continue
        try:
            cur.execute(
                "INSERT IGNORE INTO cet6_word (word, pos, meaning) VALUES (%s, %s, %s)",
                (word, pos, meaning),
            )
            if cur.rowcount > 0:
                inserted += 1
        except Exception as e:
            print(f"  跳过 {word}: {e}")
    conn.commit()
    cur.close()
    conn.close()
    print(f"导入完成: {inserted} 个新词 (总词条 {len(words)})")

if __name__ == "__main__":
    if len(sys.argv) > 1:
        # 从本地文件读取
        with open(sys.argv[1], encoding="utf-8") as f:
            words = json.load(f)
    else:
        words = fetch_json()
    import_to_db(words)
