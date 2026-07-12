package com.github.overz.bdd;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PUBLISH_QUIET_PROPERTY_NAME;

/**
 * Runner dos testes BDD (Cucumber) sobre os arquivos {@code .feature} em
 * {@code src/test/resources/features}, derivados de {@code requisitos-final.md}.
 *
 * <p>Cenários de funcionalidades ainda não implementadas carregam a tag {@code @pendente}
 * e são excluídos da execução pelo filtro abaixo — o build permanece verde enquanto o
 * backlog avança. Para ativar um cenário: implemente as steps correspondentes em
 * {@code com.github.overz.bdd.steps} (substituindo o {@code PendingException}) e remova
 * a tag {@code @pendente} do cenário ou da funcionalidade.
 *
 * <p>Quando as steps precisarem do contexto Spring (ex.: MockMvc contra os endpoints
 * reais), adicione uma classe de configuração anotada com
 * {@code @CucumberContextConfiguration} + {@code @SpringBootTest} neste pacote —
 * o {@code cucumber-spring} já está no classpath.
 */
@Suite(failIfNoTests = false)
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.github.overz.bdd")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "not @pendente")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty")
@ConfigurationParameter(key = PLUGIN_PUBLISH_QUIET_PROPERTY_NAME, value = "true")
public class CucumberTest {
}
