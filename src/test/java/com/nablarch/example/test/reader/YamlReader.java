package com.nablarch.example.test.reader;

import nablarch.test.core.reader.PoiXlsReader;
import nablarch.test.core.reader.TestDataReader;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * YAML ファイルからテストデータを読み込む {@link TestDataReader} 実装。
 *
 * <p>対象クラスと同名の {@code .ntf.yaml} または {@code .yaml} ファイルがファイルシステム上に存在する場合は
 * YAML から読み込む。{@code .ntf.yaml} を優先し、存在しない場合は {@code .yaml} を探す。
 * どちらも存在しない場合は {@link PoiXlsReader} に処理を委譲する。</p>
 *
 * <h2>NTF の呼び出し規約</h2>
 * <ul>
 *   <li>{@code resourceName} = ソースディレクトリ相対パス（例: {@code src/test/java/com/example}）</li>
 *   <li>{@code dataName} = {@code ClassName/sheetName}（例: {@code FooTest/setUpDb}）</li>
 * </ul>
 * <p>YAML ファイルは {@code src/test/java/{package}/ClassName.ntf.yaml}（または {@code .yaml}）に
 * Excel と同ディレクトリで配置する。</p>
 */
public class YamlReader implements TestDataReader {

    private List<List<String>> lines;
    private Iterator<List<String>> iterator;
    private TestDataReader delegate = new PoiXlsReader();
    private boolean usingYaml = false;

    /** DI インジェクション用セッター（設定しない場合は new PoiXlsReader() を使用） */
    public void setDelegate(TestDataReader delegate) {
        this.delegate = delegate;
    }

    @Override
    public void open(String resourceName, String dataName) {
        File yamlFile = resolveYamlFile(resourceName, dataName);
        if (!yamlFile.exists()) {
            usingYaml = false;
            delegate.open(resourceName, dataName);
            return;
        }
        try (InputStream is = new FileInputStream(yamlFile)) {
            String sheetName = extractSheetName(dataName);
            Map<String, Object> root = loadYaml(is);
            @SuppressWarnings("unchecked")
            Map<String, Object> sheetData = (Map<String, Object>) root.get(sheetName);
            if (sheetData == null) {
                // YAML ファイルは存在するがこのシートは未移行 → Excel に委譲
                usingYaml = false;
                delegate.open(resourceName, dataName);
                return;
            }
            usingYaml = true;
            lines = convertToLines(sheetData);
            iterator = lines.iterator();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read YAML: " + yamlFile, e);
        }
    }

    @Override
    public List<String> readLine() {
        if (usingYaml) {
            return (iterator != null && iterator.hasNext()) ? iterator.next() : null;
        }
        return delegate.readLine();
    }

    @Override
    public void close() {
        if (usingYaml) {
            lines = null;
            iterator = null;
        } else {
            delegate.close();
        }
    }

    @Override
    public boolean isResourceExisting(String resourceName, String dataName) {
        if (resolveYamlFile(resourceName, dataName).exists()) {
            return true;
        }
        return delegate.isResourceExisting(resourceName, dataName);
    }

    @Override
    public boolean isDataExisting(String resourceName, String dataName) {
        File yamlFile = resolveYamlFile(resourceName, dataName);
        if (!yamlFile.exists()) {
            return delegate.isDataExisting(resourceName, dataName);
        }
        try (InputStream is = new FileInputStream(yamlFile)) {
            String sheetName = extractSheetName(dataName);
            Map<String, Object> root = loadYaml(is);
            return root.containsKey(sheetName);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read YAML: " + yamlFile, e);
        }
    }

    // -----------------------------------------------------------------------
    // private helpers
    // -----------------------------------------------------------------------

    /**
     * {@code resourceName}（ソースディレクトリ相対パス）と {@code dataName}
     * （{@code ClassName/sheetName}）から YAML ファイルを返す。
     *
     * <p>{@code .ntf.yaml} を優先して探し、存在しない場合は {@code .yaml} を返す。</p>
     *
     * <pre>
     * resourceName = "src/test/java/com/example"
     * dataName     = "FooTest/setUpDb"
     * → new File("src/test/java/com/example/FooTest.ntf.yaml")  // 存在すれば
     * → new File("src/test/java/com/example/FooTest.yaml")      // なければこちら
     * </pre>
     */
    private File resolveYamlFile(String resourceName, String dataName) {
        String className = extractClassName(dataName);
        String base = resourceName + "/" + className;
        File ntfYaml = new File(base + ".ntf.yaml");
        return ntfYaml.exists() ? ntfYaml : new File(base + ".yaml");
    }

    /** {@code dataName} の '/' より前のクラス名部分を返す */
    private String extractClassName(String dataName) {
        int slash = dataName.indexOf('/');
        return (slash >= 0) ? dataName.substring(0, slash) : dataName;
    }

    /** {@code dataName} の '/' より後のシート名部分を返す */
    private String extractSheetName(String dataName) {
        int slash = dataName.lastIndexOf('/');
        return (slash >= 0) ? dataName.substring(slash + 1) : dataName;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadYaml(InputStream is) {
        LoaderOptions opts = new LoaderOptions();
        Yaml yaml = new Yaml(new LinkedHashMapConstructor(opts));
        return (Map<String, Object>) yaml.load(is);
    }

    /**
     * YAML シートデータを Excel 相当の行リストに変換する。
     * 各ブロックにつき: [ブロック名行] → [ヘッダ行] → [データ行...]
     */
    @SuppressWarnings("unchecked")
    private List<List<String>> convertToLines(Map<String, Object> sheetData) {
        List<List<String>> result = new ArrayList<>();
        if (sheetData == null) {
            return result;
        }
        for (Map.Entry<String, Object> entry : sheetData.entrySet()) {
            String blockName = entry.getKey();
            Object value = entry.getValue();

            result.add(Collections.singletonList(blockName));

            if (value instanceof List) {
                List<?> outerList = (List<?>) value;
                if (outerList.isEmpty()) continue;
                if (outerList.get(0) instanceof List) {
                    // #RawRows: List<List<Object>> → 行をそのまま出力
                    for (Object rowObj : outerList) {
                        List<Object> rawRow = (List<Object>) rowObj;
                        List<String> dataRow = new ArrayList<>();
                        for (Object cell : rawRow) {
                            String v = (cell == null) ? null : String.valueOf(cell);
                            // Keep empty strings as-is; only YAML null should become null.
                            dataRow.add(v);
                        }
                        result.add(dataRow);
                    }
                } else {
                    // #ListMap: List<Map<Object, Object>>
                    List<Map<Object, Object>> rows = (List<Map<Object, Object>>) value;
                    List<Object> rawKeys = new ArrayList<>(rows.get(0).keySet());
                    List<String> headers = new ArrayList<>();
                    for (Object k : rawKeys) {
                        headers.add(k == null ? "" : String.valueOf(k));
                    }
                    result.add(headers);

                    boolean firstRowIsSentinel = rows.get(0).values().stream().allMatch(v -> v == null);
                    int dataStart = firstRowIsSentinel ? 1 : 0;
                    for (int r = dataStart; r < rows.size(); r++) {
                        Map<Object, Object> row = rows.get(r);
                        List<String> dataRow = new ArrayList<>();
                        for (int i = 0; i < rawKeys.size(); i++) {
                            Object cell = row.get(rawKeys.get(i));
                            String v = (cell == null) ? null : String.valueOf(cell);
                            // Keep empty strings as-is; only YAML null should become null.
                            dataRow.add(v);
                        }
                        result.add(dataRow);
                    }
                }
            } else if (value instanceof Map) {
                // 後方互換: __columns__ キーのみを持つ旧形式の空テーブル定義
                Map<String, Object> mapValue = (Map<String, Object>) value;
                Object colsObj = mapValue.get("__columns__");
                if (colsObj instanceof List) {
                    List<Object> colList = (List<Object>) colsObj;
                    List<String> headers = new ArrayList<>();
                    for (Object c : colList) {
                        headers.add(c == null ? "" : String.valueOf(c));
                    }
                    result.add(headers);
                }
            }
        }
        return result;
    }

    private static class LinkedHashMapConstructor extends Constructor {
        LinkedHashMapConstructor(LoaderOptions opts) {
            super(Object.class, opts);
        }

        @Override
        protected Map<Object, Object> createDefaultMap(int initSize) {
            return new LinkedHashMap<>(initSize);
        }
    }
}
