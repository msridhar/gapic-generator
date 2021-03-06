/* Copyright 2017 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.api.codegen.transformer;

import com.google.api.codegen.config.FieldConfig;
import com.google.api.codegen.config.FieldModel;
import com.google.api.codegen.config.InterfaceConfig;
import com.google.api.codegen.config.InterfaceContext;
import com.google.api.codegen.config.MethodConfig;
import com.google.api.codegen.config.MethodContext;
import com.google.api.codegen.config.MethodModel;
import com.google.api.codegen.config.PageStreamingConfig;
import com.google.api.codegen.config.SampleSpec.SampleType;
import com.google.api.codegen.config.SingleResourceNameConfig;
import com.google.api.codegen.config.TypeModel;
import com.google.api.codegen.gapic.ServiceMessages;
import com.google.api.codegen.metacode.InitCodeContext.InitCodeOutputType;
import com.google.api.codegen.viewmodel.ApiCallableImplType;
import com.google.api.codegen.viewmodel.ApiMethodDocView;
import com.google.api.codegen.viewmodel.CallableMethodDetailView;
import com.google.api.codegen.viewmodel.CallingForm;
import com.google.api.codegen.viewmodel.ClientMethodType;
import com.google.api.codegen.viewmodel.ListMethodDetailView;
import com.google.api.codegen.viewmodel.ParamDocView;
import com.google.api.codegen.viewmodel.PathTemplateCheckView;
import com.google.api.codegen.viewmodel.RequestObjectMethodDetailView;
import com.google.api.codegen.viewmodel.RequestObjectParamView;
import com.google.api.codegen.viewmodel.SimpleParamDocView;
import com.google.api.codegen.viewmodel.StaticLangApiMethodView;
import com.google.api.codegen.viewmodel.StaticLangApiMethodView.Builder;
import com.google.api.codegen.viewmodel.UnpagedListCallableMethodDetailView;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * StaticLangApiMethodTransformer generates view objects from method definitions for static
 * languages.
 */
public class StaticLangApiMethodTransformer {
  private final LongRunningTransformer lroTransformer = new LongRunningTransformer();
  private final StaticLangResourceObjectTransformer resourceObjectTransformer =
      new StaticLangResourceObjectTransformer();
  private final HeaderRequestParamTransformer headerRequestParamTransformer =
      new HeaderRequestParamTransformer();
  private final SampleTransformer sampleTransformer;

  public StaticLangApiMethodTransformer(SampleTransformer sampleTransformer) {
    this.sampleTransformer = sampleTransformer;
  }

  public StaticLangApiMethodTransformer() {
    this(SampleTransformer.create(SampleType.IN_CODE));
  }

  // TODO: Currently overriden in CSharpApiMethodTransformer. Inspect whether the same logic applies
  // to Java as well.
  /** Generates method views for all methods in an interface. */
  public List<StaticLangApiMethodView> generateApiMethods(InterfaceContext interfaceContext) {
    throw new UnsupportedOperationException(
        "unimplemented: StaticLangApiMethodTransformer:generateApiMethods");
  }

  // Used by: Java
  public StaticLangApiMethodView generatePagedFlattenedMethod(MethodContext context) {
    return generatePagedFlattenedMethod(context, Collections.<ParamWithSimpleDoc>emptyList());
  }

  // Used by: CSharp (and indirectly by Java via the overload above)
  public StaticLangApiMethodView generatePagedFlattenedMethod(
      MethodContext context, List<ParamWithSimpleDoc> additionalParams) {
    SurfaceNamer namer = context.getNamer();
    StaticLangApiMethodView.Builder methodViewBuilder = StaticLangApiMethodView.newBuilder();

    setCommonFields(context, methodViewBuilder);
    methodViewBuilder.name(
        namer.getApiMethodName(
            context.getMethodModel(), context.getMethodConfig().getVisibility()));
    methodViewBuilder.exampleName(
        namer.getApiMethodExampleName(context.getInterfaceConfig(), context.getMethodModel()));
    PageStreamingConfig pageStreaming = context.getMethodConfig().getPageStreaming();
    FieldConfig resourceFieldConfig = pageStreaming.getResourcesFieldConfig();
    String callerResponseTypeName =
        namer.getAndSaveCallerPagedResponseTypeName(context, resourceFieldConfig);
    setListMethodFields(context, Synchronicity.Sync, methodViewBuilder);
    setFlattenedMethodFields(
        context,
        additionalParams,
        Synchronicity.Sync,
        methodViewBuilder,
        context.getCallingForms());

    return methodViewBuilder
        .type(ClientMethodType.PagedFlattenedMethod)
        .callerResponseTypeName(callerResponseTypeName)
        .build();
  }

  // Used by: CSharp
  public StaticLangApiMethodView generatePagedFlattenedAsyncMethod(
      MethodContext context, List<ParamWithSimpleDoc> additionalParams) {
    MethodModel methodModel = context.getMethodModel();
    SurfaceNamer namer = context.getNamer();
    StaticLangApiMethodView.Builder methodViewBuilder = StaticLangApiMethodView.newBuilder();

    setCommonFields(context, methodViewBuilder);
    methodViewBuilder.name(
        namer.getAsyncApiMethodName(methodModel, context.getMethodConfig().getVisibility()));
    methodViewBuilder.exampleName(namer.getAsyncApiMethodExampleName(methodModel));
    PageStreamingConfig pageStreaming = context.getMethodConfig().getPageStreaming();
    FieldConfig resourceFieldConfig = pageStreaming.getResourcesFieldConfig();
    String callerResponseTypeName =
        namer.getAndSaveCallerAsyncPagedResponseTypeName(context, resourceFieldConfig);

    setListMethodFields(context, Synchronicity.Async, methodViewBuilder);
    setFlattenedMethodFields(
        context,
        additionalParams,
        Synchronicity.Async,
        methodViewBuilder,
        context.getCallingForms());

    return methodViewBuilder
        .type(ClientMethodType.PagedFlattenedAsyncMethod)
        .callerResponseTypeName(callerResponseTypeName)
        .build();
  }

  // Used by: Java
  public StaticLangApiMethodView generatePagedRequestObjectMethod(MethodContext context) {
    return generatePagedRequestObjectMethod(context, Collections.<ParamWithSimpleDoc>emptyList());
  }

  // Used by: CSharp (and indirectly by Java via the overload above)
  public StaticLangApiMethodView generatePagedRequestObjectMethod(
      MethodContext context, List<ParamWithSimpleDoc> additionalParams) {
    MethodModel method = context.getMethodModel();
    SurfaceNamer namer = context.getNamer();
    StaticLangApiMethodView.Builder methodViewBuilder = StaticLangApiMethodView.newBuilder();
    PageStreamingConfig pageStreaming = context.getMethodConfig().getPageStreaming();
    FieldConfig resourceFieldConfig = pageStreaming.getResourcesFieldConfig();
    String callerResponseTypeName =
        namer.getAndSaveCallerPagedResponseTypeName(context, resourceFieldConfig);

    setCommonFields(context, methodViewBuilder);
    methodViewBuilder.name(
        namer.getApiMethodName(method, context.getMethodConfig().getVisibility()));
    methodViewBuilder.exampleName(
        namer.getApiMethodExampleName(context.getInterfaceConfig(), method));

    setListMethodFields(context, Synchronicity.Sync, methodViewBuilder);
    setRequestObjectMethodFields(
        context,
        namer.getPagedCallableMethodName(method),
        Synchronicity.Sync,
        additionalParams,
        methodViewBuilder,
        context.getCallingForms());

    return methodViewBuilder
        .type(ClientMethodType.PagedRequestObjectMethod)
        .callerResponseTypeName(callerResponseTypeName)
        .build();
  }

  // Used by: CSharp
  public StaticLangApiMethodView generatePagedRequestObjectAsyncMethod(
      MethodContext context, List<ParamWithSimpleDoc> additionalParams) {
    MethodModel method = context.getMethodModel();
    SurfaceNamer namer = context.getNamer();
    StaticLangApiMethodView.Builder methodViewBuilder = StaticLangApiMethodView.newBuilder();

    PageStreamingConfig pageStreaming = context.getMethodConfig().getPageStreaming();
    FieldConfig resourceFieldConfig = pageStreaming.getResourcesFieldConfig();
    String callerResponseTypeName =
        namer.getAndSaveCallerAsyncPagedResponseTypeName(context, resourceFieldConfig);
    setCommonFields(context, methodViewBuilder);
    methodViewBuilder.name(
        namer.getAsyncApiMethodName(
            context.getMethodModel(), context.getMethodConfig().getVisibility()));
    methodViewBuilder.exampleName(namer.getAsyncApiMethodExampleName(method));
    setListMethodFields(context, Synchronicity.Async, methodViewBuilder);
    setRequestObjectMethodFields(
        context,
        namer.getPagedCallableMethodName(method),
        Synchronicity.Async,
        additionalParams,
        methodViewBuilder,
        context.getCallingForms());

    return methodViewBuilder
        .type(ClientMethodType.AsyncPagedRequestObjectMethod)
        .callerResponseTypeName(callerResponseTypeName)
        .build();
  }

  // Used by: Java
  public StaticLangApiMethodView generatePagedCallableMethod(MethodContext context) {
    MethodModel method = context.getMethodModel();
    SurfaceNamer namer = context.getNamer();
    StaticLangApiMethodView.Builder methodViewBuilder = StaticLangApiMethodView.newBuilder();

    setCommonFields(context, methodViewBuilder);
    methodViewBuilder.name(namer.getPagedCallableMethodName(method));
    methodViewBuilder.exampleName(namer.getPagedCallableMethodExampleName(method));
    setListMethodFields(context, Synchronicity.Sync, methodViewBuilder);
    setCallableMethodFields(
        context, namer.getPagedCallableName(method), methodViewBuilder, context.getCallingForms());

    return methodViewBuilder.type(ClientMethodType.PagedCallableMethod).build();
  }

  // Used by: Java
  public StaticLangApiMethodView generateUnpagedListCallableMethod(MethodContext context) {
    MethodModel method = context.getMethodModel();
    SurfaceNamer namer = context.getNamer();
    StaticLangApiMethodView.Builder methodViewBuilder = StaticLangApiMethodView.newBuilder();

    setCommonFields(context, methodViewBuilder);
    methodViewBuilder.name(namer.getCallableMethodName(method));
    methodViewBuilder.exampleName(namer.getCallableMethodExampleName(method));
    setListMethodFields(context, Synchronicity.Sync, methodViewBuilder);
    setCallableMethodFields(
        context, namer.getCallableName(method), methodViewBuilder, context.getCallingForms());

    String getResourceListCallName =
        namer.getFieldGetFunctionName(
            context.getFeatureConfig(),
            context.getMethodConfig().getPageStreaming().getResourcesFieldConfig());

    String resourceListParseFunction = "";
    PageStreamingConfig pageStreaming = context.getMethodConfig().getPageStreaming();
    FieldConfig resourceFieldConfig = pageStreaming.getResourcesFieldConfig();
    if (context.getFeatureConfig().useResourceNameConverters(resourceFieldConfig)) {
      resourceListParseFunction =
          namer.getResourceTypeParseListMethodName(context.getTypeTable(), resourceFieldConfig);
    }

    UnpagedListCallableMethodDetailView unpagedListCallableDetails =
        UnpagedListCallableMethodDetailView.newBuilder()
            .resourceListGetFunction(getResourceListCallName)
            .resourceListParseFunction(resourceListParseFunction)
            .build();
    methodViewBuilder.unpagedListCallableMethod(unpagedListCallableDetails);

    methodViewBuilder.responseTypeName(
        context
            .getMethodModel()
            .getAndSaveResponseTypeName(context.getTypeTable(), context.getNamer()));

    return methodViewBuilder.type(ClientMethodType.UnpagedListCallableMethod).build();
  }

  public StaticLangApiMethodView generateFlattenedAsyncMethod(
      MethodContext context, ClientMethodType type) {
    return generateFlattenedAsyncMethod(context, Collections.<ParamWithSimpleDoc>emptyList(), type);
  }

  // Used by: CSharp
  public StaticLangApiMethodView generateFlattenedAsyncMethod(
      MethodContext context, List<ParamWithSimpleDoc> additionalParams, ClientMethodType type) {
    MethodModel method = context.getMethodModel();
    SurfaceNamer namer = context.getNamer();
    StaticLangApiMethodView.Builder methodViewBuilder = StaticLangApiMethodView.newBuilder();

    setCommonFields(context, methodViewBuilder);
    methodViewBuilder.name(
        namer.getAsyncApiMethodName(method, context.getMethodConfig().getVisibility()));
    methodViewBuilder.exampleName(namer.getCallableMethodExampleName(method));
    methodViewBuilder.callableName(namer.getCallableName(method));
    setFlattenedMethodFields(
        context,
        additionalParams,
        Synchronicity.Async,
        methodViewBuilder,
        context.getCallingForms());
    setStaticLangAsyncReturnTypeName(context, methodViewBuilder);

    return methodViewBuilder.type(type).build();
  }

  // Used by: Java
  public StaticLangApiMethodView generateFlattenedMethod(MethodContext context) {
    return generateFlattenedMethod(context, Collections.<ParamWithSimpleDoc>emptyList());
  }

  // Used by: CSharp (and indirectly by Java via the overload above)
  public StaticLangApiMethodView generateFlattenedMethod(
      MethodContext context, List<ParamWithSimpleDoc> additionalParams) {
    MethodModel method = context.getMethodModel();
    SurfaceNamer namer = context.getNamer();
    StaticLangApiMethodView.Builder methodViewBuilder = StaticLangApiMethodView.newBuilder();

    setCommonFields(context, methodViewBuilder);
    methodViewBuilder.name(
        namer.getApiMethodName(method, context.getMethodConfig().getVisibility()));
    methodViewBuilder.exampleName(
        namer.getApiMethodExampleName(context.getInterfaceConfig(), method));
    methodViewBuilder.callableName(namer.getCallableName(method));
    setFlattenedMethodFields(
        context,
        additionalParams,
        Synchronicity.Sync,
        methodViewBuilder,
        context.getCallingForms());
    setStaticLangReturnTypeName(context, methodViewBuilder);

    return methodViewBuilder.type(ClientMethodType.FlattenedMethod).build();
  }

  // Used by: Java
  public StaticLangApiMethodView generateRequestObjectMethod(MethodContext context) {
    return generateRequestObjectMethod(context, Collections.<ParamWithSimpleDoc>emptyList());
  }

  // Used by: C# (and indirectly by Java via the overload above)
  public StaticLangApiMethodView generateRequestObjectMethod(
      MethodContext context, List<ParamWithSimpleDoc> additionalParams) {
    MethodModel method = context.getMethodModel();
    SurfaceNamer namer = context.getNamer();
    StaticLangApiMethodView.Builder methodViewBuilder = StaticLangApiMethodView.newBuilder();

    setCommonFields(context, methodViewBuilder);
    methodViewBuilder.name(
        namer.getApiMethodName(method, context.getMethodConfig().getVisibility()));
    methodViewBuilder.exampleName(
        namer.getApiMethodExampleName(context.getInterfaceConfig(), method));
    setRequestObjectMethodFields(
        context,
        namer.getCallableMethodName(method),
        Synchronicity.Sync,
        additionalParams,
        methodViewBuilder,
        context.getCallingForms());
    setStaticLangReturnTypeName(context, methodViewBuilder);

    return methodViewBuilder.type(ClientMethodType.RequestObjectMethod).build();
  }

  public StaticLangApiMethodView generateRequestObjectAsyncMethod(MethodContext context) {
    return generateRequestObjectAsyncMethod(
        context,
        Collections.<ParamWithSimpleDoc>emptyList(),
        ClientMethodType.AsyncRequestObjectCallSettingsMethod);
  }

  // Used by: CSharp
  public StaticLangApiMethodView generateRequestObjectAsyncMethod(
      MethodContext context, List<ParamWithSimpleDoc> additionalParams, ClientMethodType type) {
    MethodModel method = context.getMethodModel();
    SurfaceNamer namer = context.getNamer();
    StaticLangApiMethodView.Builder methodViewBuilder = StaticLangApiMethodView.newBuilder();

    setCommonFields(context, methodViewBuilder);
    methodViewBuilder.name(
        namer.getAsyncApiMethodName(method, context.getMethodConfig().getVisibility()));
    methodViewBuilder.exampleName(namer.getAsyncApiMethodExampleName(method));
    setRequestObjectMethodFields(
        context,
        namer.getCallableAsyncMethodName(method),
        Synchronicity.Async,
        additionalParams,
        methodViewBuilder,
        context.getCallingForms());
    setStaticLangAsyncReturnTypeName(context, methodViewBuilder);

    return methodViewBuilder.type(type).build();
  }

  // Used by: Java
  public StaticLangApiMethodView generateCallableMethod(MethodContext context) {
    MethodModel method = context.getMethodModel();
    SurfaceNamer namer = context.getNamer();
    StaticLangApiMethodView.Builder methodViewBuilder = StaticLangApiMethodView.newBuilder();

    setCommonFields(context, methodViewBuilder);
    methodViewBuilder.name(namer.getCallableMethodName(method));
    methodViewBuilder.exampleName(context.getNamer().getCallableMethodExampleName(method));
    setCallableMethodFields(
        context, namer.getCallableName(method), methodViewBuilder, context.getCallingForms());
    methodViewBuilder.responseTypeName(
        context
            .getMethodModel()
            .getAndSaveResponseTypeName(context.getTypeTable(), context.getNamer()));

    return methodViewBuilder.type(ClientMethodType.CallableMethod).build();
  }

  // Used by: CSharp
  public StaticLangApiMethodView generateGrpcStreamingRequestObjectMethod(MethodContext context) {
    MethodModel method = context.getMethodModel();
    SurfaceNamer namer = context.getNamer();
    StaticLangApiMethodView.Builder methodViewBuilder = StaticLangApiMethodView.newBuilder();

    setCommonFields(context, methodViewBuilder);
    methodViewBuilder.name(
        namer.getGrpcStreamingApiMethodName(method, context.getMethodConfig().getVisibility()));
    methodViewBuilder.exampleName(
        namer.getGrpcStreamingApiMethodExampleName(context.getInterfaceConfig(), method));
    setRequestObjectMethodFields(
        context,
        namer.getCallableMethodName(method),
        Synchronicity.Sync,
        methodViewBuilder,
        context.getCallingForms());
    setStaticLangGrpcStreamingReturnTypeName(context, methodViewBuilder);

    return methodViewBuilder.type(ClientMethodType.RequestObjectMethod).build();
  }

  // Used by CSharp.
  public StaticLangApiMethodView generateGrpcStreamingFlattenedMethod(
      MethodContext context, List<ParamWithSimpleDoc> additionalParams) {
    MethodModel method = context.getMethodModel();
    SurfaceNamer namer = context.getNamer();
    StaticLangApiMethodView.Builder methodViewBuilder = StaticLangApiMethodView.newBuilder();

    setCommonFields(context, methodViewBuilder);
    methodViewBuilder.name(
        namer.getGrpcStreamingApiMethodName(method, context.getMethodConfig().getVisibility()));
    methodViewBuilder.exampleName(
        namer.getGrpcStreamingApiMethodExampleName(context.getInterfaceConfig(), method));
    methodViewBuilder.callableName(namer.getCallableName(method));
    setFlattenedMethodFields(
        context,
        additionalParams,
        Synchronicity.Sync,
        methodViewBuilder,
        context.getCallingForms());
    setStaticLangGrpcStreamingReturnTypeName(context, methodViewBuilder);

    return methodViewBuilder.type(ClientMethodType.FlattenedMethod).build();
  }

  public StaticLangApiMethodView generateOperationRequestObjectMethod(MethodContext context) {
    return generateOperationRequestObjectMethod(
        context, Collections.<ParamWithSimpleDoc>emptyList());
  }

  // Used by: CSharp
  public StaticLangApiMethodView generateOperationRequestObjectMethod(
      MethodContext context, List<ParamWithSimpleDoc> additionalParams) {
    MethodModel method = context.getMethodModel();
    SurfaceNamer namer = context.getNamer();
    StaticLangApiMethodView.Builder methodViewBuilder = StaticLangApiMethodView.newBuilder();

    setCommonFields(context, methodViewBuilder);
    methodViewBuilder.name(
        namer.getApiMethodName(method, context.getMethodConfig().getVisibility()));
    methodViewBuilder.exampleName(
        namer.getApiMethodExampleName(context.getInterfaceConfig(), method));
    setRequestObjectMethodFields(
        context,
        namer.getCallableMethodName(method),
        Synchronicity.Sync,
        additionalParams,
        methodViewBuilder,
        context.getCallingForms());
    methodViewBuilder.operationMethod(lroTransformer.generateDetailView(context));
    TypeModel returnType = context.getLongRunningConfig().getReturnType();
    methodViewBuilder.responseTypeName(context.getTypeTable().getAndSaveNicknameFor(returnType));

    return methodViewBuilder.type(ClientMethodType.OperationRequestObjectMethod).build();
  }

  public StaticLangApiMethodView generateOperationFlattenedMethod(
      MethodContext context, List<ParamWithSimpleDoc> additionalParams) {
    MethodModel method = context.getMethodModel();
    SurfaceNamer namer = context.getNamer();
    StaticLangApiMethodView.Builder methodViewBuilder = StaticLangApiMethodView.newBuilder();

    setCommonFields(context, methodViewBuilder);
    methodViewBuilder.name(
        namer.getApiMethodName(method, context.getMethodConfig().getVisibility()));
    methodViewBuilder.exampleName(
        namer.getApiMethodExampleName(context.getInterfaceConfig(), method));
    methodViewBuilder.callableName(namer.getCallableName(method));
    setFlattenedMethodFields(
        context,
        additionalParams,
        Synchronicity.Sync,
        methodViewBuilder,
        context.getCallingForms());
    methodViewBuilder.operationMethod(lroTransformer.generateDetailView(context));
    TypeModel returnType = context.getLongRunningConfig().getReturnType();
    methodViewBuilder.responseTypeName(context.getTypeTable().getAndSaveNicknameFor(returnType));
    return methodViewBuilder.type(ClientMethodType.OperationFlattenedMethod).build();
  }

  // Used by Java.
  public StaticLangApiMethodView generateAsyncOperationFlattenedMethod(MethodContext context) {
    return generateAsyncOperationFlattenedMethod(
        context,
        Collections.<ParamWithSimpleDoc>emptyList(),
        ClientMethodType.AsyncOperationFlattenedMethod,
        false);
  }

  // Used by CSharp (and indirectly by Java via the overload above)
  public StaticLangApiMethodView generateAsyncOperationFlattenedMethod(
      MethodContext context,
      List<ParamWithSimpleDoc> additionalParams,
      ClientMethodType type,
      boolean requiresOperationMethod) {
    MethodModel method = context.getMethodModel();
    SurfaceNamer namer = context.getNamer();
    StaticLangApiMethodView.Builder methodViewBuilder = StaticLangApiMethodView.newBuilder();

    setCommonFields(context, methodViewBuilder);
    methodViewBuilder.name(
        namer.getAsyncApiMethodName(
            context.getMethodModel(), context.getMethodConfig().getVisibility()));
    methodViewBuilder.exampleName(namer.getAsyncApiMethodExampleName(method));
    methodViewBuilder.callableName(namer.getCallableName(method));
    setFlattenedMethodFields(
        context,
        additionalParams,
        Synchronicity.Async,
        methodViewBuilder,
        context.getCallingForms());
    if (requiresOperationMethod) {
      methodViewBuilder.operationMethod(lroTransformer.generateDetailView(context));
    }
    TypeModel returnType = context.getLongRunningConfig().getReturnType();
    methodViewBuilder.responseTypeName(context.getTypeTable().getAndSaveNicknameFor(returnType));
    methodViewBuilder.operationMethod(lroTransformer.generateDetailView(context));

    return methodViewBuilder.type(type).build();
  }

  // Used by: Java
  public StaticLangApiMethodView generateAsyncOperationRequestObjectMethod(MethodContext context) {
    return generateAsyncOperationRequestObjectMethod(
        context, Collections.<ParamWithSimpleDoc>emptyList(), false);
  }

  // Used by: CSharp (and indirectly by Java via the overload above)
  public StaticLangApiMethodView generateAsyncOperationRequestObjectMethod(
      MethodContext context,
      List<ParamWithSimpleDoc> additionalParams,
      boolean requiresOperationMethod) {
    MethodModel method = context.getMethodModel();
    SurfaceNamer namer = context.getNamer();
    StaticLangApiMethodView.Builder methodViewBuilder = StaticLangApiMethodView.newBuilder();

    setCommonFields(context, methodViewBuilder);
    methodViewBuilder.name(
        namer.getAsyncApiMethodName(
            context.getMethodModel(), context.getMethodConfig().getVisibility()));
    methodViewBuilder.exampleName(namer.getAsyncApiMethodExampleName(method));
    setRequestObjectMethodFields(
        context,
        namer.getOperationCallableMethodName(method),
        Synchronicity.Async,
        additionalParams,
        methodViewBuilder,
        context.getCallingForms());
    if (requiresOperationMethod) {
      // Only for protobuf-based APIs.
      methodViewBuilder.operationMethod(lroTransformer.generateDetailView(context));
    }
    if (context.isLongRunningMethodContext()) {
      // Only for protobuf-based APIs.
      TypeModel returnType = context.getLongRunningConfig().getReturnType();
      methodViewBuilder.responseTypeName(context.getTypeTable().getAndSaveNicknameFor(returnType));
      methodViewBuilder.operationMethod(lroTransformer.generateDetailView(context));
    } else {
      throw new IllegalArgumentException(
          "Discovery-based APIs do not have LongRunning operations.");
    }
    return methodViewBuilder.type(ClientMethodType.AsyncOperationRequestObjectMethod).build();
  }

  public StaticLangApiMethodView generateOperationCallableMethod(MethodContext context) {
    MethodModel method = context.getMethodModel();
    SurfaceNamer namer = context.getNamer();
    StaticLangApiMethodView.Builder methodViewBuilder = StaticLangApiMethodView.newBuilder();

    setCommonFields(context, methodViewBuilder);
    methodViewBuilder.name(namer.getOperationCallableMethodName(method));
    methodViewBuilder.exampleName(context.getNamer().getOperationCallableMethodExampleName(method));
    setCallableMethodFields(
        context,
        namer.getOperationCallableName(method),
        methodViewBuilder,
        context.getCallingForms());
    TypeModel returnType = context.getLongRunningConfig().getReturnType();
    methodViewBuilder.responseTypeName(context.getTypeTable().getAndSaveNicknameFor(returnType));
    methodViewBuilder.operationMethod(lroTransformer.generateDetailView(context));

    return methodViewBuilder.type(ClientMethodType.OperationCallableMethod).build();
  }

  private void setCommonFields(
      MethodContext context, StaticLangApiMethodView.Builder methodViewBuilder) {
    MethodModel method = context.getMethodModel();
    SurfaceNamer namer = context.getNamer();
    InterfaceConfig interfaceConfig = context.getInterfaceConfig();

    String requestTypeName =
        method.getAndSaveRequestTypeName(context.getTypeTable(), context.getNamer());
    methodViewBuilder.serviceRequestTypeName(requestTypeName);
    methodViewBuilder.serviceRequestTypeConstructor(namer.getTypeConstructor(requestTypeName));
    methodViewBuilder.serviceConstructorName(
        namer.getApiWrapperClassConstructorName(context.getInterfaceConfig()));

    setServiceResponseTypeName(context, methodViewBuilder);

    methodViewBuilder.apiClassName(namer.getApiWrapperClassName(interfaceConfig));
    methodViewBuilder.apiVariableName(namer.getApiWrapperVariableName(interfaceConfig));
    methodViewBuilder.stubName(namer.getStubName(context.getTargetInterface()));
    methodViewBuilder.settingsGetterName(namer.getSettingsFunctionName(method));
    methodViewBuilder.callableName(context.getNamer().getCallableName(method));
    methodViewBuilder.modifyMethodName(namer.getModifyMethodName(context));
    methodViewBuilder.grpcStreamingType(context.getMethodConfig().getGrpcStreamingType());
    methodViewBuilder.visibility(
        namer.getVisiblityKeyword(context.getMethodConfig().getVisibility()));
    methodViewBuilder.releaseLevelAnnotation(
        namer.getReleaseAnnotation(context.getMethodConfig().getReleaseLevel()));

    ServiceMessages messages = new ServiceMessages();
    if (context.isLongRunningMethodContext()) {
      methodViewBuilder.hasReturnValue(
          !context.getLongRunningConfig().getReturnType().isEmptyType());
    } else {
      methodViewBuilder.hasReturnValue(!method.isOutputTypeEmpty());
    }
    methodViewBuilder.headerRequestParams(
        headerRequestParamTransformer.generateHeaderRequestParams(context));
  }

  protected void setServiceResponseTypeName(
      MethodContext context, StaticLangApiMethodView.Builder methodViewBuilder) {
    SurfaceNamer namer = context.getNamer();
    if (context.getMethodConfig().isGrpcStreaming()) {
      // Only applicable for protobuf APIs.
      String returnTypeFullName =
          namer.getGrpcStreamingApiReturnTypeName(context, context.getTypeTable());
      String returnTypeNickname = context.getTypeTable().getAndSaveNicknameFor(returnTypeFullName);
      methodViewBuilder.serviceResponseTypeName(returnTypeNickname);
    } else {
      String responseTypeName =
          context
              .getMethodModel()
              .getAndSaveResponseTypeName(context.getTypeTable(), context.getNamer());
      methodViewBuilder.serviceResponseTypeName(responseTypeName);
    }
  }

  private void setListMethodFields(
      MethodContext context,
      Synchronicity synchronicity,
      StaticLangApiMethodView.Builder methodViewBuilder) {
    MethodModel method = context.getMethodModel();
    ImportTypeTable typeTable = context.getTypeTable();
    SurfaceNamer namer = context.getNamer();
    PageStreamingConfig pageStreaming = context.getMethodConfig().getPageStreaming();
    String requestTypeName =
        method.getAndSaveRequestTypeName(context.getTypeTable(), context.getNamer());
    String responseTypeName =
        method.getAndSaveResponseTypeName(context.getTypeTable(), context.getNamer());

    FieldConfig resourceFieldConfig = pageStreaming.getResourcesFieldConfig();
    FieldModel resourceField = resourceFieldConfig.getField();

    String resourceTypeName;

    if (context.getFeatureConfig().useResourceNameFormatOption(resourceFieldConfig)) {
      resourceTypeName = namer.getAndSaveElementResourceTypeName(typeTable, resourceFieldConfig);
    } else {
      resourceTypeName = typeTable.getAndSaveNicknameForElementType(resourceField);
    }

    String iterateMethodName =
        namer.getPagedResponseIterateMethod(context.getFeatureConfig(), resourceFieldConfig);

    String resourceFieldName = namer.getFieldName(resourceField);
    String resourceFieldGetterName =
        namer.getFieldGetFunctionName(context.getFeatureConfig(), resourceFieldConfig);

    methodViewBuilder.listMethod(
        ListMethodDetailView.newBuilder()
            .requestTypeName(requestTypeName)
            .responseTypeName(responseTypeName)
            .resourceTypeName(resourceTypeName)
            .iterateMethodName(iterateMethodName)
            .resourceFieldName(resourceFieldName)
            .resourcesFieldGetFunction(resourceFieldGetterName)
            .build());

    switch (synchronicity) {
      case Sync:
        methodViewBuilder.responseTypeName(
            namer.getAndSavePagedResponseTypeName(context, resourceFieldConfig));
        break;
      case Async:
        methodViewBuilder.responseTypeName(
            namer.getAndSaveAsyncPagedResponseTypeName(context, resourceFieldConfig));
        break;
    }
  }

  private void setFlattenedMethodFields(
      MethodContext context,
      List<ParamWithSimpleDoc> additionalParams,
      Synchronicity synchronicity,
      StaticLangApiMethodView.Builder methodViewBuilder,
      List<CallingForm> callingForms) {
    MethodModel method = context.getMethodModel();
    SurfaceNamer namer = context.getNamer();
    Collection<FieldConfig> fieldConfigs =
        context.getFlatteningConfig().getFlattenedFieldConfigs().values();
    sampleTransformer.generateSamples(
        methodViewBuilder, context, fieldConfigs, InitCodeOutputType.FieldList, callingForms);

    methodViewBuilder.doc(
        ApiMethodDocView.newBuilder()
            .mainDocLines(namer.getDocLines(method, context.getMethodConfig()))
            .paramDocs(getMethodParamDocs(context, fieldConfigs, additionalParams))
            .throwsDocLines(namer.getThrowsDocLines(context.getMethodConfig()))
            .returnsDocLines(
                namer.getReturnDocLines(
                    context.getSurfaceInterfaceContext(), context, synchronicity))
            .build());

    List<RequestObjectParamView> params = new ArrayList<>();
    for (FieldConfig fieldConfig : fieldConfigs) {
      params.add(resourceObjectTransformer.generateRequestObjectParam(context, fieldConfig));
    }
    methodViewBuilder.forwardingMethodParams(params);
    List<RequestObjectParamView> nonforwardingParams = new ArrayList<>(params);
    nonforwardingParams.addAll(ParamWithSimpleDoc.asRequestObjectParamViews(additionalParams));
    methodViewBuilder.methodParams(nonforwardingParams);
    methodViewBuilder.requestObjectParams(params);

    methodViewBuilder.pathTemplateChecks(generatePathTemplateChecks(context, fieldConfigs));
  }

  private void setRequestObjectMethodFields(
      MethodContext context,
      String callableMethodName,
      Synchronicity sync,
      StaticLangApiMethodView.Builder methodViewBuilder,
      List<CallingForm> callingForms) {
    setRequestObjectMethodFields(
        context,
        callableMethodName,
        sync,
        Collections.<ParamWithSimpleDoc>emptyList(),
        methodViewBuilder,
        callingForms);
  }

  private void setRequestObjectMethodFields(
      MethodContext context,
      String callableMethodName,
      Synchronicity sync,
      List<ParamWithSimpleDoc> additionalParams,
      StaticLangApiMethodView.Builder methodViewBuilder,
      List<CallingForm> callingForms) {
    MethodModel method = context.getMethodModel();
    SurfaceNamer namer = context.getNamer();
    List<ParamDocView> paramDocs = new ArrayList<>();
    paramDocs.addAll(getRequestObjectParamDocs(context));
    paramDocs.addAll(ParamWithSimpleDoc.asParamDocViews(additionalParams));
    methodViewBuilder.doc(
        ApiMethodDocView.newBuilder()
            .mainDocLines(namer.getDocLines(method, context.getMethodConfig()))
            .paramDocs(paramDocs)
            .throwsDocLines(namer.getThrowsDocLines(context.getMethodConfig()))
            .returnsDocLines(
                namer.getReturnDocLines(context.getSurfaceInterfaceContext(), context, sync))
            .build());

    sampleTransformer.generateSamples(
        methodViewBuilder,
        context,
        context.getMethodConfig().getRequiredFieldConfigs(),
        InitCodeOutputType.SingleObject,
        callingForms);

    methodViewBuilder.methodParams(new ArrayList<RequestObjectParamView>());
    methodViewBuilder.requestObjectParams(new ArrayList<RequestObjectParamView>());
    methodViewBuilder.pathTemplateChecks(new ArrayList<PathTemplateCheckView>());

    RequestObjectMethodDetailView.Builder detailBuilder =
        RequestObjectMethodDetailView.newBuilder();
    detailBuilder.accessModifier(
        context.getNamer().getVisiblityKeyword(context.getMethodConfig().getVisibility()));
    detailBuilder.callableMethodName(callableMethodName);
    methodViewBuilder.requestObjectMethod(detailBuilder.build());
  }

  private void setCallableMethodFields(
      MethodContext context,
      String callableName,
      Builder methodViewBuilder,
      List<CallingForm> callingForms) {
    MethodModel method = context.getMethodModel();
    methodViewBuilder.doc(
        ApiMethodDocView.newBuilder()
            .mainDocLines(context.getNamer().getDocLines(method, context.getMethodConfig()))
            .paramDocs(new ArrayList<ParamDocView>())
            .throwsDocLines(new ArrayList<String>())
            .build());

    sampleTransformer.generateSamples(
        methodViewBuilder,
        context,
        context.getMethodConfig().getRequiredFieldConfigs(),
        InitCodeOutputType.SingleObject,
        callingForms);

    methodViewBuilder.methodParams(new ArrayList<RequestObjectParamView>());
    methodViewBuilder.requestObjectParams(new ArrayList<RequestObjectParamView>());
    methodViewBuilder.pathTemplateChecks(new ArrayList<PathTemplateCheckView>());

    String requestTypeFullName =
        context.getMethodModel().getInputTypeName(context.getTypeTable()).getFullName();
    String requestType = context.getTypeTable().getAndSaveNicknameFor(requestTypeFullName);

    String genericAwareResponseTypeFullName =
        context.getNamer().getGenericAwareResponseTypeName(context);
    String genericAwareResponseType =
        context.getTypeTable().getAndSaveNicknameFor(genericAwareResponseTypeFullName);

    MethodConfig methodConfig = context.getMethodConfig();
    ApiCallableImplType callableImplType = ApiCallableImplType.SimpleApiCallable;
    if (methodConfig.isGrpcStreaming()) {
      callableImplType = ApiCallableImplType.of(methodConfig.getGrpcStreamingType());
    } else if (methodConfig.isBatching()) {
      callableImplType = ApiCallableImplType.BatchingApiCallable;
    }

    methodViewBuilder.callableMethod(
        CallableMethodDetailView.newBuilder()
            .requestType(requestType)
            .genericAwareResponseType(genericAwareResponseType)
            .callableName(callableName)
            .interfaceTypeName(
                context.getNamer().getApiCallableTypeName(callableImplType.serviceMethodType()))
            .build());
  }

  private void setStaticLangAsyncReturnTypeName(
      MethodContext context, StaticLangApiMethodView.Builder methodViewBuilder) {
    SurfaceNamer namer = context.getNamer();
    String returnTypeFullName = namer.getStaticLangAsyncReturnTypeName(context);
    String returnTypeNickname = context.getTypeTable().getAndSaveNicknameFor(returnTypeFullName);
    methodViewBuilder.responseTypeName(returnTypeNickname);
  }

  private void setStaticLangReturnTypeName(
      MethodContext context, StaticLangApiMethodView.Builder methodViewBuilder) {
    SurfaceNamer namer = context.getNamer();
    String returnTypeFullName = namer.getStaticLangReturnTypeName(context);
    String returnTypeNickname = context.getTypeTable().getAndSaveNicknameFor(returnTypeFullName);
    methodViewBuilder.responseTypeName(returnTypeNickname);
  }

  private void setStaticLangGrpcStreamingReturnTypeName(
      MethodContext context, StaticLangApiMethodView.Builder methodViewBuilder) {
    SurfaceNamer namer = context.getNamer();
    // use the api return type name as the surface return type name
    String returnTypeFullName =
        namer.getGrpcStreamingApiReturnTypeName(context, context.getTypeTable());
    String returnTypeNickname = context.getTypeTable().getAndSaveNicknameFor(returnTypeFullName);
    methodViewBuilder.responseTypeName(returnTypeNickname);
  }

  private List<PathTemplateCheckView> generatePathTemplateChecks(
      MethodContext context, Iterable<FieldConfig> fieldConfigs) {
    List<PathTemplateCheckView> pathTemplateChecks = new ArrayList<>();
    if (!context.getFeatureConfig().enableStringFormatFunctions()) {
      return pathTemplateChecks;
    }
    for (FieldConfig fieldConfig : fieldConfigs) {
      if (!fieldConfig.useValidation()) {
        // Don't generate a path template check if fieldConfig is not configured to use validation.
        continue;
      }
      FieldModel field = fieldConfig.getField();
      ImmutableMap<String, String> fieldNamePatterns =
          context.getMethodConfig().getFieldNamePatterns();
      String entityName = fieldNamePatterns.get(field.getSimpleName());
      if (entityName != null) {
        SingleResourceNameConfig resourceNameConfig =
            context.getSingleResourceNameConfig(entityName);
        if (resourceNameConfig == null) {
          String methodName = context.getMethodModel().getSimpleName();
          throw new IllegalStateException(
              "No collection config with id '"
                  + entityName
                  + "' required by configuration for method '"
                  + methodName
                  + "'");
        }
        PathTemplateCheckView.Builder check = PathTemplateCheckView.newBuilder();
        check.pathTemplateName(
            context
                .getNamer()
                .getPathTemplateName(context.getInterfaceConfig(), resourceNameConfig));
        check.paramName(context.getNamer().getVariableName(field));
        check.allowEmptyString(shouldAllowEmpty(context, field));
        check.validationMessageContext(
            context
                .getNamer()
                .getApiMethodName(
                    context.getMethodModel(), context.getMethodConfig().getVisibility()));
        pathTemplateChecks.add(check.build());
      }
    }
    return pathTemplateChecks;
  }

  private boolean shouldAllowEmpty(MethodContext context, FieldModel field) {
    for (FieldModel requiredField : context.getMethodConfig().getRequiredFields()) {
      if (requiredField.equals(field)) {
        return false;
      }
    }
    return true;
  }

  private List<ParamDocView> getMethodParamDocs(
      MethodContext context,
      Iterable<FieldConfig> fieldConfigs,
      List<ParamWithSimpleDoc> additionalParamDocs) {
    MethodModel method = context.getMethodModel();
    List<ParamDocView> allDocs = new ArrayList<>();
    if (method.getRequestStreaming()) {
      allDocs.addAll(ParamWithSimpleDoc.asParamDocViews(additionalParamDocs));
      return allDocs;
    }
    for (FieldConfig fieldConfig : fieldConfigs) {
      FieldModel field = fieldConfig.getField();
      SimpleParamDocView.Builder paramDoc = SimpleParamDocView.newBuilder();
      paramDoc.paramName(context.getNamer().getVariableName(field));
      paramDoc.typeName(context.getTypeTable().getAndSaveNicknameFor(field));

      List<String> docLines = null;
      MethodConfig methodConfig = context.getMethodConfig();
      if (methodConfig.isPageStreaming()
          && methodConfig.getPageStreaming().hasPageSizeField()
          && field.equals(methodConfig.getPageStreaming().getPageSizeField())) {
        docLines =
            Arrays.asList(
                new String[] {
                  "The maximum number of resources contained in the underlying API",
                  "response. The API may return fewer values in a page, even if",
                  "there are additional values to be retrieved."
                });
      } else if (methodConfig.isPageStreaming()
          && field.equals(methodConfig.getPageStreaming().getRequestTokenField())) {
        docLines =
            Arrays.asList(
                new String[] {
                  "A page token is used to specify a page of values to be returned.",
                  "If no page token is specified (the default), the first page",
                  "of values will be returned. Any page token used here must have",
                  "been generated by a previous call to the API."
                });
      } else {
        docLines = context.getNamer().getDocLines(field);
      }

      paramDoc.lines(docLines);

      allDocs.add(paramDoc.build());
    }
    allDocs.addAll(ParamWithSimpleDoc.asParamDocViews(additionalParamDocs));
    return allDocs;
  }

  public List<SimpleParamDocView> getRequestObjectParamDocs(MethodContext context) {
    MethodModel method = context.getMethodModel();
    SimpleParamDocView doc =
        SimpleParamDocView.newBuilder()
            .paramName("request")
            .typeName(method.getAndSaveRequestTypeName(context.getTypeTable(), context.getNamer()))
            .lines(
                Arrays.<String>asList(
                    "The request object containing all of the parameters for the API call."))
            .build();
    return ImmutableList.of(doc);
  }
}
