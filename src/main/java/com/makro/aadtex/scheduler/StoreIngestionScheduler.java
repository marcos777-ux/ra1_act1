package com.makro.aadtex.scheduler;

import com.makro.aadtex.service.StoreIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** La tarea recurrente no finaliza la aplicación; escanea de nuevo cada 5000 ms. */
@Component
public class StoreIngestionScheduler {
    private static final Logger log = LoggerFactory.getLogger(StoreIngestionScheduler.class);
    private final StoreIngestionService ingestion;

    public StoreIngestionScheduler(StoreIngestionService ingestion) {
        this.ingestion = ingestion;
    }

    @Scheduled(fixedRate = 5000)
    public void processStoreBatches() {
        try {
            ingestion.processStoreBatches();
        } catch (Exception e) {
            log.error("Error global de exploración; se reintentará en el próximo ciclo", e);
        }
    }
}
