package com.nablarch.example.test.http;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * YAML テストデータに対応した REST テスト用アノテーション。
 *
 * <p>{@code nablarch.test.junit5.extension.http.RestTest} の代替。
 * {@link YamlRestTestExtension} を使用するため、物理 Excel ファイルがなくても
 * {@code .ntf.yaml} ファイルのみでセットアップが行われる。</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(YamlRestTestExtension.class)
public @interface YamlRestTest {
}
