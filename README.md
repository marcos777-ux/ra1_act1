# RA1_ACT1 - AadTex Store Ingestion Engine

Proyecto de Acceso a Datos (AAD) - RA1: Manejo de Ficheros.

## Repositorio GitHub
https://github.com/marcos777-ux/ra1_act1

## Tecnologias
- Java 21
- Spring Boot
- Maven
- NIO.2
- AES-128
- ZipInputStream
- UTF-8
- JUnit 5

## Ejecucion

mvn clean verify
mvn spring-boot:run

## Funcionalidades
- Ingestion automatica cada 5 segundos.
- Procesamiento de CSV internacionales.
- Descifrado AES-128.
- Descompresion ZIP.
- Copia de imagenes con buffers de 4 KB.
- Deteccion y aislamiento de archivos defectuosos.
- Generacion de resumenes JSON.
