package org.codesfactory.api;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MozartAPIClient {
    public List<ModelInfo> getModelInfo() throws Exception {
        List<ModelInfo> list = new ArrayList<>();
        list.add(new ModelInfo("MELODY_GEM", "Melody Gem"));
        list.add(new ModelInfo("BASS_GEM", "Bass Gem"));
        return list;
    }

    public byte[] generate(File midiFile, File metaFile) throws Exception {
        // APIを呼び出す代わりに、ダミーの空データを返す（実際には接続エラーなどをエミュレートするか、あるいは何かしらの動作をさせる）
        // 現時点では、UI上の生成処理がクラッシュしないように空のバイト配列を返すか、例外を投げます。
        // 空のバイト配列を返す場合、PianoRoll.javaでデコード処理が失敗する可能性があるので、
        // 動作を見て必要に応じて調整します。
        return new byte[0];
    }
}
