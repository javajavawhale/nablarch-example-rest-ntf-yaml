package com.nablarch.example.test.http;

import nablarch.test.core.http.RestTestSupport;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * YAML テストデータに対応した {@link RestTestSupport} のサブクラス。
 *
 * <p>{@code setUpDbIfSheetExists} をオーバーライドし、対応する {@code .yaml} が
 * クラスパス上に存在する場合は物理 Excel ファイルチェック（{@code File.exists()}）を
 * バイパスして {@code setUpDb} を直接呼び出す。</p>
 *
 * <ul>
 *   <li>YAML に該当シートがある場合: {@code setUpDb(sheetName)} を呼び出す</li>
 *   <li>YAML に該当シートがない場合: スキップ（親クラスの {@code isExisting=false} と同等）</li>
 *   <li>YAML 自体が存在しない場合: 親クラスの Excel ベース処理に委譲</li>
 * </ul>
 */
public class YamlRestTestSupport extends RestTestSupport {

    private static final String YAML_EXTENSION = ".yaml";

    public YamlRestTestSupport(Class<?> testClass) {
        super(testClass);
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code .yaml} が存在する場合、YAML ファイルを直接参照してシートの有無を確認する。
     * これにより、親クラスの {@code getSheet()} による物理 Excel ファイル要求をバイパスする。</p>
     */
    @Override
    protected void setUpDbIfSheetExists(String sheetName) {
        String packagePath = testDescription.getTestClass().getPackage().getName().replace('.', '/');
        String className = testDescription.getTestClassSimpleName();
        File yamlFile = new File("src/test/java/" + packagePath + "/" + className + YAML_EXTENSION);

        if (!yamlFile.exists()) {
            // YAML なし → 親クラス（Excel ベース）に委譲
            super.setUpDbIfSheetExists(sheetName);
            return;
        }

        try (InputStream is = new FileInputStream(yamlFile)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> root = (Map<String, Object>) new Yaml(new LoaderOptions()).load(is);
            if (root != null && root.containsKey(sheetName)) {
                setUpDb(sheetName);
            }
            // シートなし → スキップ（親クラスの isExisting()=false と同等）
        } catch (IOException e) {
            throw new RuntimeException("Failed to read YAML: " + yamlFile, e);
        }
    }
}
