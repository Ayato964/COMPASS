# MORTM API リファレンス (v2)

AIによる音楽生成モデル **MORTM4.5 / 4.5D** を FastAPI でラップした Web API です。
従来モデル（Flash / Pro）と、新規追加の **基盤(Foundation)モデル** および
**生成SFTモデル** の 3 系統すべてを同一エンドポイントで扱えます。

---

## 1. ベースURL

| 環境 | URL |
| --- | --- |
| ローカル | `http://localhost:8000` |
| 公開 (ngrok) | `https://c7be-133-43-172-128.ngrok-free.app` |

> ngrok の無料ドメインは再起動ごとに変わります。最新URLは `/model_info` の疎通で確認してください。
> ブラウザ以外のクライアントから ngrok 無料枠を叩く場合は、警告ページ回避のため
> `ngrok-skip-browser-warning: 1` ヘッダを付けると確実です。

---

## 2. エンドポイント一覧

| Method | Path | 概要 |
| --- | --- | --- |
| `POST` | `/model_info` | 利用可能な全モデルのメタデータ取得 |
| `POST` | `/generate` | 指定モデルで音楽(MIDI)を生成 |

エンドポイントの引数は v1 から**変更していません**（`midi` 単一入力の後方互換も維持）。

---

## 3. `POST /model_info`

利用可能な全モデルの情報を返します。リクエストボディ不要。

### レスポンス例 (抜粋)

```json
{
  "0": {
    "model_name": "MORTM4.5-Flash-Preview",
    "description": "...",
    "tag": {
      "instruments": ["PIANO", "SAX"],
      "model": "pretrained",
      "task": ["Meta2MIDI"],
      "type": "mortm",
      "version": "4.5"
    },
    "rule": {
      "input_midi": false,
      "multi_instrument": true,
      "temperature": true,
      "top_p": true,
      "number_of_generation": true,
      "gen_note_dense": true,
      "gen_measure_count": false,
      "send_genre": false
    },
    "model_folder_path": "/.../data/models/MORTM45_Flash"
  }
}
```

### `tag.model` の値（= モデル系統）

| 値 | 系統 | 生成方式 |
| --- | --- | --- |
| `pretrained` | 従来モデル (Flash / Pro) | `<MGEN>` から旋律/コードを生成 |
| `foundation` | 基盤モデル (4.5D-80M) | **条件なし(`<EOS>`のみ)で自律生成** |
| `sft_gen` | 生成SFTモデル (4.5D-80M-SFT-Gen) | メタ＋任意文脈から**中間部(CONST)**を生成 |

### `rule` フラグ（フロントUIの出し分け用）

| キー | 意味 |
| --- | --- |
| `input_midi` | MIDIファイル入力に対応するか |
| `multi_instrument` | 複数楽器同時生成に対応するか |
| `temperature` / `top_p` | 温度 / nucleus サンプリング指定可否 |
| `number_of_generation` | 生成数(`num_gems`)指定可否 |
| `send_context_past` / `_future` / `_condition` | 過去/未来/条件MIDIの送信可否 |
| `gen_measure_count` | 生成小節数(`genfield_measure`)指定可否 |
| `gen_note_dense` | 音符密度(`gen_note_dense`)指定可否 |
| `send_genre` | ジャンル(`genre`)指定可否 |
| `send_chord` / `send_chord_progression` | コード指定可否 |

---

## 4. `POST /generate`

`multipart/form-data` で送信します。

### フォームフィールド

| フィールド | 型 | 必須 | 説明 |
| --- | --- | --- | --- |
| `meta_json` | file (JSON) | ✅ | 生成パラメータ (下記 `GenerateMeta`) |
| `midi` | file (MIDI) | – | 旧互換入力。内部的に `conditions_midi` として扱う |
| `past_midi` | file (MIDI) | – | 過去(前方)コンテキスト |
| `conditions_midi` | file (MIDI) | – | 条件コンテキスト |
| `future_midi` | file (MIDI) | – | 未来(後方)コンテキスト |

> `midi` と `conditions_midi` の同時指定はエラー(400)です。

### `meta_json` (GenerateMeta) スキーマ

| フィールド | 型 | デフォルト | 説明 |
| --- | --- | --- | --- |
| `model_type` | string | **必須** | 使用モデル名 (`model_name`)。表記揺れ吸収あり |
| `program` | List[int\|str] | **必須** | 楽器。`PIANO`(0–6) / `SAX`(64–67) |
| `tempo` | int | **必須** | BPM (>0)。MIDI書き出しに使用 |
| `task` | string | `"Meta2MIDI"` | タスク。`generate`/`melodygen` 等の別名可 |
| `key` | string | `null` | キー (例 `Cm`, `CM`) |
| `num_gems` | int | `1` | 生成数 (≥1) |
| `genfield_measure` | int | `8` | 生成小節数 (1–8 にクランプ) |
| `gen_note_dense` | int \| object | `{"PIANO":4}` | 音符密度 (1–10)。楽器別指定可 |
| `p` | float | `0.95` | nucleus sampling p (0<p≤1) |
| `temperature` | float | `1.0` | 温度 (>0) |
| `genre` | List[string] | `null` | **(新規)** ジャンル。SFT-Gen のみ有効。§6参照 |
| `thinking` | bool | `true` | **(新規)** CoT(`<thinking>`)。SFT-Gen でコンテキストありの時のみ有効。§4.1参照 |
| `chord_item` | List[string] | `null` | コード進行 (従来モデル用) |
| `chord_times` | List[float] | `null` | 各コード開始時刻 (chord_item と同数) |
| `split_measure` | int | `999` | 分割小節数 |
| `ai_continue_mode` | bool | `false` | 継続生成モード (従来モデル用) |

> 未対応モデルに渡したフィールド（例: 基盤モデルへの `genre`）は安全に無視されます。

### レスポンス

| 状況 | Content-Type | 本文 |
| --- | --- | --- |
| 単一生成 | `audio/midi` | 生成された `.mid` |
| 複数生成 (`num_gems`>1) | `application/zip` | 複数 `.mid` を ZIP 化 |
| 解析系タスク | `application/json` | `{"result":"success","data": {...}, "reason": ...}` |

エラー: `400`(入力不正) / `422`(meta_json 不正) / `500`(生成失敗) / `503`(初期化未完了)。

### reason (返り値)

すべての生成で **reason** を返します（生成数分のリスト）。

- **ファイル応答 (MIDI/ZIP)**: HTTP ヘッダ `X-Generation-Reason` に
  **URL エンコードされた JSON** を格納（CORS で公開済み。ブラウザ JS から読めます）。
- **JSON 応答**: ボディの `reason` フィールド。

reason の各要素:

| キー | 説明 |
| --- | --- |
| `source` | `model_cot`(モデルが CoT で予測) / `request`(リクエストの実効メタ) / `foundation` |
| `task` | `meta` / `meta_past` / `meta_future` / `unconditional` |
| `thinking` | CoT を要求したか |
| `instruments` | 楽器 |
| `note_density` | 楽器別の音符密度 (1–10) |
| `gen_measure_count` | 生成小節数 |
| `genre` | ジャンル |
| `key` | キー |

例 (ヘッダをデコードしたもの):
```json
[{"source":"model_cot","task":"meta_past","instruments":["PIANO"],
  "note_density":{"PIANO":4},"gen_measure_count":8,"genre":["jazz"],"key":"Cm"}]
```

### 4.1 CoT (Chain-of-Thought) と reason

SFT-Gen は、条件付きシステムタグの末尾に `<thinking>` を置くと、`<MGEN>` の直後に
**フルメタ (key/density/genre/length/楽器) を「考えて」から** MIDI を生成します。
この予測メタが `reason` (`source="model_cot"`) になります。ユーザーが省略した属性を
モデルが補完するため、その判断を確認できます。

- CoT は **コンテキストあり (`past_midi`/`future_midi`) かつ `thinking=true`** のとき有効です
  (`meta_past`/`meta_future` で学習。`thinking` のないパターンも正規です)。
- モデルの CoT 発火は本来確率的 (約25%) なため、API は `<MGEN>` 直後に `<SYSTEM>` を注入して
  **CoT 分岐を確定的に誘発**し、reason を安定して `source="model_cot"` で返します。
- CoT が無効な場合 (コンテキスト無し / `thinking=false`) は、reason は
  **リクエストの実効メタ** (`source="request"`) にフォールバックし、**常に非 null** です。
- 例: `program=["PIANO"]` だけ指定 + コンテキスト + `thinking=true` →
  reason に `note_density`/`genre`/`key`/`gen_measure_count` などモデルの判断が入ります。

---

## 5. モデル系統ごとの使い方

### (A) 従来モデル — `MORTM4.5-Flash-Preview` / `-Pro-Preview` / `*-Preview2`
`tag.model = "pretrained"`。メタ情報から旋律を生成。`task` に `Meta2MIDI`(既定),
`Chord2MIDI`, `MIDI2Chord`, `MIDI2Meta` 等。コンテキストMIDI・コード指定に対応。

```bash
curl -X POST "$URL/generate" \
  -F 'meta_json={"model_type":"MORTM4.5-Flash-Preview","program":["PIANO"],"tempo":120,"key":"Cm","genfield_measure":8,"gen_note_dense":{"PIANO":4}};type=application/json' \
  -o generated.mid
```

### (B) 基盤モデル — `MORTM4.5D-80M`
`tag.model = "foundation"`。**条件入力なし(`<EOS>`のみ)で自律生成**します。
フロントからMIDIや楽器・キー等を送る必要はありません（送っても無視）。
有効パラメータは `temperature` / `p` / `num_gems` / `tempo`(書出用) のみ。

```bash
curl -X POST "$URL/generate" \
  -F 'meta_json={"model_type":"MORTM4.5D-80M","program":["PIANO"],"tempo":120,"temperature":1.0,"p":0.95,"num_gems":1};type=application/json' \
  -o generated.mid
```

### (C) 生成SFTモデル — `MORTM4.5D-80M-SFT-Gen`
`tag.model = "sft_gen"`。メタ情報（楽器/密度/キー/ジャンル/生成小節数）と、
**任意の過去または未来コンテキスト**から**中間部(CONST)**を生成します。
可変入出力長(1–8小節)・42ジャンルに対応。

入力MIDIの割り当て:
- `past_midi` → 過去文脈として続きを生成 (meta_past)
- `future_midi` → 未来文脈に向けて生成 (meta_future)
- `conditions_midi` / `midi` → 過去文脈として扱う（後方互換）
- いずれも無し → メタ情報のみから生成 (meta)

```bash
# メタのみから生成
curl -X POST "$URL/generate" \
  -F 'meta_json={"model_type":"MORTM4.5D-80M-SFT-Gen","program":["PIANO","SAX"],"tempo":120,"genre":["jazz"],"key":"Cm","genfield_measure":4,"gen_note_dense":{"PIANO":4,"SAX":3},"num_gems":1};type=application/json' \
  -o generated.mid

# 過去コンテキストMIDIから続きを生成 (CoT 有効 → reason にモデルの判断が入る)
#   -D でヘッダを保存すると X-Generation-Reason を確認できる
curl -X POST "$URL/generate" \
  -F 'meta_json={"model_type":"MORTM4.5D-80M-SFT-Gen","program":["PIANO"],"tempo":120,"genre":["jazz"],"thinking":true};type=application/json' \
  -F "past_midi=@./intro.mid;type=audio/midi" \
  -D headers.txt -o generated.mid
```

> 属性を省略 (例: `key` を渡さない) して `thinking=true` + コンテキストを与えると、
> モデルが欠けた属性を CoT で補完し、その判断が `reason` で返ります。

---

## 6. ジャンル一覧 (SFT-Gen, 42種)

`80s`, `90s`, `alternative`, `ambient`, `blues`, `celtic`, `chillout`,
`classical`, `country`, `dance`, `drumnbass`, `easylistening`, `electronic`,
`electropop`, `experimental`, `folk`, `funk`, `hiphop`, `house`, `indie`,
`instrumentalpop`, `instrumentalrock`, `jazz`, `jazzfusion`, `latin`, `lounge`,
`metal`, `newage`, `orchestral`, `pop`, `popfolk`, `poprock`, `punkrock`,
`reggae`, `rock`, `soundtrack`, `swing`, `symphonic`, `synthpop`, `techno`,
`trance`, `world`

> 1〜2個指定できます。未知のジャンルは無視されます。

---

## 7. 対応楽器

| 名称 | MIDI program | 種別 |
| --- | --- | --- |
| `PIANO` | 0–6 | 多声 (polyphonic) |
| `SAX` | 64–67 | 単声 (monophonic) |

---

## 8. 補足

- **モデル管理**: `data/models/<dir>/data.json` を起動時に自動スキャン。各フォルダに
  `config.json` と `model.pth` が必要です。`data.json` の `model_name` で系統が決まります。
- **キャッシュ/VRAM**: 直近使用モデルを LRU キャッシュ（既定 1 体）。VRAM 閾値超過時に自動アンロード。
- 環境変数: `MOZART_CACHE_SIZE`(既定1) / `MOZART_VRAM_THRESHOLD`(既定0.5)。
