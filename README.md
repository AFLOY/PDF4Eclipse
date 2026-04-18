# Pdf4Eclipse Fork

`Pdf4Eclipse` のメンテナンス fork です。  
元プロジェクトである [Borisvl/Pdf4Eclipse](https://github.com/Borisvl/Pdf4Eclipse) をベースに、現在の Eclipse / Java 環境で使いやすいように移植と整備を行っています。

このリポジトリは、upstream の機能と構成を尊重しつつ、以下を主目的として更新したものです。

- 現行 Eclipse で読み込みやすいプロジェクト構成への整理
- Java 21 前提の開発環境への調整
- PDE に加えて Tycho でもビルドできる構成の追加
- fork 用の bundle / feature / update site ID への置き換え
- Eclipse 上で PDF を開いて利用できる状態までの保守

## Origin

この fork は以下の upstream を元にしています。

- Pdf4Eclipse: [Borisvl/Pdf4Eclipse](https://github.com/Borisvl/Pdf4Eclipse)
- PDFrenderer fork: [Borisvl/PDFrenderer](https://github.com/Borisvl/PDFrenderer)

PDF 描画系については、upstream と同様に以下の系統を前提としています。

- Sun PDF Renderer 系の fork
- JPedal LGPL 版

ライセンス表記は upstream の内容を尊重しています。詳細は各モジュール内のライセンス表記を確認してください。

## About This Fork

この fork は新規実装ではなく、**既存の Pdf4Eclipse を移植・保守するための fork** です。  
Java パッケージ名は互換性を優先して大部分を upstream のまま維持し、主に次の要素を fork 向けに更新しています。

- OSGi bundle ID
- feature ID
- repository / update site 構成
- editor / command / preference などの公開 ID
- Java 21 で問題になりやすい古い実装や警告の一部

## Codex Usage

この fork の整備では OpenAI Codex を利用しています。

Codex の主な利用範囲は以下です。

- upstream 構成の取り込み補助
- Eclipse plugin / PDE / Tycho 構成の整理
- Java 21 対応のための機械的な修正
- README やビルド設定などの補助的な整備
- Eclipse 上での警告や依存関係の解消作業の補助

最終的な方針決定、確認、採否判断はリポジトリ管理者が行う前提です。  
したがって、この fork は **Codex を用いて整備した fork** であり、**Codex が単独で生成した独自実装プロジェクト** ではありません。

## Current Target Environment

- Eclipse 2026 系を想定
- Java 21

実運用上は、Eclipse 上で PDF エディタとして開ける状態を最低ラインとしています。

## Repository Layout

- `io.github.h44bc.pdf4eclipse`
  - メインの PDF エディタプラグイン
- `io.github.h44bc.pdf4eclipse.help`
  - help コンテンツ
- `io.github.h44bc.pdf4eclipse.jpedal`
  - JPedal 関連のバイナリ同梱プラグイン
- `io.github.h44bc.pdf4eclipse.feature`
  - Eclipse feature
- `io.github.h44bc.pdf4eclipse.repository`
  - p2 update site 定義

## Build

ルートで Tycho ビルドできます。

```bash
mvn -U clean verify
```

生成される update site は次です。

```text
io.github.h44bc.pdf4eclipse.repository/target/repository
```

Eclipse への導入は `Help > Install New Software...` からこの repository を指定します。

## Development In Eclipse

Eclipse PDE では、各モジュールを既存プロジェクトとして import して利用します。

対象プロジェクト:

- `io.github.h44bc.pdf4eclipse`
- `io.github.h44bc.pdf4eclipse.help`
- `io.github.h44bc.pdf4eclipse.jpedal`
- `io.github.h44bc.pdf4eclipse.feature`
- `io.github.h44bc.pdf4eclipse.repository`

実行時は `Eclipse Application` の Run Configuration を使い、必要に応じて `Add Required Plug-ins` を実行してください。

## Compatibility Policy

この fork は、まず **Eclipse 上で PDF を開いて使えること** を優先しています。  
大規模な設計変更や描画系の置き換えは現時点では目的にしていません。

そのため、以下の方針を取っています。

- upstream の挙動互換を優先
- Java パッケージ名の全面変更は後回し
- レンダラ実装の全面刷新は行わない
- 破壊的変更より保守的な移植を優先

## Notes

- このリポジトリは upstream の公式後継ではありません。
- upstream と完全な動作一致を保証するものではありません。
- 生成物や設定の一部は、現代の Eclipse / Java 環境で扱いやすいように再構成されています。

## Acknowledgements

元の `Pdf4Eclipse` を公開した Boris von Loesch 氏と、関連する描画ライブラリの作者に感謝します。  
この fork はそれら既存成果物の上に成り立っています。
