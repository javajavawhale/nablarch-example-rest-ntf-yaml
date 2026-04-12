package com.nablarch.example.test.http;

import nablarch.test.core.http.RestTestSupport;
import nablarch.test.junit5.extension.http.RestTestExtension;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * {@link YamlRestTestSupport} を生成する JUnit 5 Extension。
 *
 * <p>{@link RestTestExtension} のサブクラスであり、{@code createSupport} のみオーバーライドする。</p>
 */
public class YamlRestTestExtension extends RestTestExtension {

    @Override
    protected RestTestSupport createSupport(Object testInstance, ExtensionContext context) {
        return new YamlRestTestSupport(testInstance.getClass());
    }
}
