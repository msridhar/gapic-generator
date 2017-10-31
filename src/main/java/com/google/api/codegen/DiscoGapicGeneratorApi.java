/* Copyright 2017 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.api.codegen;

import static com.google.api.codegen.discogapic.MainDiscoGapicProviderFactory.JAVA;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.codegen.config.GapicProductConfig;
import com.google.api.codegen.config.PackageMetadataConfig;
import com.google.api.codegen.discogapic.DiscoGapicProvider;
import com.google.api.codegen.discogapic.DiscoGapicProviderFactory;
import com.google.api.codegen.discogapic.transformer.DiscoGapicNamer;
import com.google.api.codegen.discovery.DiscoveryNode;
import com.google.api.codegen.discovery.Document;
import com.google.api.codegen.gapic.GapicGeneratorConfig;
import com.google.api.codegen.transformer.SurfaceNamer;
import com.google.api.codegen.transformer.java.JavaSurfaceNamer;
import com.google.api.codegen.util.ClassInstantiator;
import com.google.api.codegen.util.java.JavaNameFormatter;
import com.google.api.tools.framework.model.ConfigSource;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.model.SimpleDiagCollector;
import com.google.api.tools.framework.snippet.Doc;
import com.google.api.tools.framework.tools.ToolOptions;
import com.google.api.tools.framework.tools.ToolOptions.Option;
import com.google.api.tools.framework.tools.ToolUtil;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.inject.TypeLiteral;
import com.google.protobuf.Message;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DiscoGapicGeneratorApi {
  public static final String DISCOVERY_DOC_OPTION_NAME = "discovery_doc";

  public static final Option<String> DISCOVERY_DOC =
      ToolOptions.createOption(
          String.class,
          DISCOVERY_DOC_OPTION_NAME,
          "The Discovery doc representing the service description.",
          "");

  public static final Option<String> OUTPUT_FILE =
      ToolOptions.createOption(
          String.class,
          "output_file",
          "The name of the output file or folder to put generated code.",
          "");

  public static final Option<List<String>> GENERATOR_CONFIG_FILES =
      ToolOptions.createOption(
          new TypeLiteral<List<String>>() {},
          "config_files",
          "The list of YAML configuration files for the code generator.",
          ImmutableList.<String>of());

  public static final Option<String> PACKAGE_CONFIG_FILE =
      ToolOptions.createOption(
          String.class, "package_config", "The package metadata configuration.", "");

  public static final Option<List<String>> ENABLED_ARTIFACTS =
      ToolOptions.createOption(
          new TypeLiteral<List<String>>() {},
          "enabled_artifacts",
          "The artifacts to be generated by the code generator.",
          ImmutableList.<String>of());

  private final ToolOptions options;

  /** Constructs a code generator api based on given options. */
  public DiscoGapicGeneratorApi(ToolOptions options) {
    this.options = options;
  }

  /** From config file paths, constructs the DiscoGapicProviders to run. */
  @VisibleForTesting
  static List<DiscoGapicProvider> getProviders(
      String discoveryDocPath,
      List<String> configFileNames,
      String packageConfigFile,
      List<String> enabledArtifacts)
      throws IOException {
    if (!new File(discoveryDocPath).exists()) {
      throw new IOException("File not found: " + discoveryDocPath);
    }
    Reader reader = new InputStreamReader(new FileInputStream(new File(discoveryDocPath)));

    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(reader);

    Document document = Document.from(new DiscoveryNode(root));

    // Read the YAML config and convert it to proto.
    if (configFileNames.size() == 0) {
      throw new IOException(String.format("--%s must be provided", GENERATOR_CONFIG_FILES.name()));
    }

    ConfigSource configSource = loadConfigFromFiles(configFileNames);
    if (configSource == null) {
      throw new IOException("Failed to load config source.");
    }

    ConfigProto configProto = (ConfigProto) configSource.getConfig();
    if (configProto == null) {
      throw new IOException("Failed to cast config proto.");
    }

    PackageMetadataConfig packageConfig = null;
    if (!Strings.isNullOrEmpty(packageConfigFile)) {
      String contents =
          new String(Files.readAllBytes(Paths.get(packageConfigFile)), StandardCharsets.UTF_8);
      packageConfig = PackageMetadataConfig.createFromString(contents);
    }
    GeneratorProto generator = configProto.getGenerator();
    String language = configProto.getLanguage();
    String defaultPackageName = configProto.getLanguageSettingsMap().get(language).getPackageName();
    SurfaceNamer surfaceNamer = null;

    if (language.equals(JAVA)) {
      surfaceNamer =
          new JavaSurfaceNamer(defaultPackageName, defaultPackageName, new JavaNameFormatter());
    }
    if (surfaceNamer == null) {
      throw new UnsupportedOperationException(
          "DiscoGapicGeneratorApi: language \"" + language + "\" not yet supported");
    }

    DiscoGapicNamer discoGapicNamer = new DiscoGapicNamer(surfaceNamer);

    GapicProductConfig productConfig =
        GapicProductConfig.create(document, configProto, discoGapicNamer);

    String factory = generator.getFactory();
    String id = generator.getId();

    DiscoGapicProviderFactory providerFactory = createProviderFactory(factory);
    GapicGeneratorConfig generatorConfig =
        GapicGeneratorConfig.newBuilder().id(id).enabledArtifacts(enabledArtifacts).build();

    return providerFactory.create(document, productConfig, generatorConfig, packageConfig);
  }

  public void run() throws Exception {

    String discoveryDocPath = options.get(DISCOVERY_DOC);
    List<String> configFileNames = options.get(GENERATOR_CONFIG_FILES);
    String packageConfigFile = options.get(PACKAGE_CONFIG_FILE);
    List<String> enabledArtifacts = options.get(ENABLED_ARTIFACTS);

    List<DiscoGapicProvider> providers =
        getProviders(discoveryDocPath, configFileNames, packageConfigFile, enabledArtifacts);

    String outputFile = options.get(OUTPUT_FILE);
    Map<String, Doc> outputFiles = Maps.newHashMap();
    for (DiscoGapicProvider provider : providers) {
      outputFiles.putAll(provider.generate());
    }
    ToolUtil.writeFiles(outputFiles, outputFile);
  }

  private static DiscoGapicProviderFactory createProviderFactory(String factory) {
    @SuppressWarnings("unchecked")
    DiscoGapicProviderFactory provider =
        ClassInstantiator.createClass(
            factory,
            DiscoGapicProviderFactory.class,
            new Class<?>[] {},
            new Object[] {},
            "generator",
            new ClassInstantiator.ErrorReporter() {
              @Override
              public void error(String message, Object... args) {
                System.err.printf(message, args);
              }
            });
    return provider;
  }

  private static List<File> pathsToFiles(List<String> configFileNames) {
    List<File> files = new ArrayList<>();

    for (String configFileName : configFileNames) {
      files.add(new File(configFileName));
    }

    return files;
  }

  private static ConfigSource loadConfigFromFiles(List<String> configFileNames) {
    List<File> configFiles = pathsToFiles(configFileNames);
    DiagCollector diagCollector = new SimpleDiagCollector();
    ImmutableMap<String, Message> supportedConfigTypes =
        ImmutableMap.<String, Message>of(
            ConfigProto.getDescriptor().getFullName(), ConfigProto.getDefaultInstance());
    return MultiYamlReader.read(diagCollector, configFiles, supportedConfigTypes);
  }
}
