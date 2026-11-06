package com.makro.aadtex;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** Smoke test: creación real de beans, autoconfiguración Jackson y scheduler. */
@SpringBootTest(properties = {"aadtex.demo.enabled=false", "aadtex.root-path=${java.io.tmpdir}/aadtex-smoke-context"})
class AadApplicationContextTest {
    @Test void contextLoads() { }
}
