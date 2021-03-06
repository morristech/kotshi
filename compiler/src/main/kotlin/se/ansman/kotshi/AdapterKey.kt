package se.ansman.kotshi

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ARRAY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.Dynamic
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.moshi.Types
import javax.lang.model.element.Element

data class AdapterKey(
    val type: TypeName,
    val jsonQualifiers: List<Element>
)

fun AdapterKey.asRuntimeType(typeVariableAccessor: (TypeVariableName) -> CodeBlock): CodeBlock =
    type.asRuntimeType(typeVariableAccessor)

val AdapterKey.suggestedAdapterName: String
    get() = "${jsonQualifiers.joinToString("") { it.simpleName }}${type.baseAdapterName}Adapter".decapitalize()

private val TypeName.baseAdapterName: String
    get() {
        return when (this) {
            is ClassName -> simpleNames.joinToString("")
            Dynamic -> "dynamic"
            is LambdaTypeName -> "lambda"
            is ParameterizedTypeName ->
                typeArguments.joinToString("") { it.baseAdapterName } + rawType.baseAdapterName
            is TypeVariableName -> name
            is WildcardTypeName -> "wildcard"
        }
    }

private fun TypeName.asRuntimeType(typeVariableAccessor: (TypeVariableName) -> CodeBlock): CodeBlock =
    when (this) {
        is ParameterizedTypeName ->
            CodeBlock.builder()
                .add("%T.newParameterizedType(%T::class.javaObjectType", Types::class.java, if (rawType == ARRAY) {
                    // Arrays are special, you cannot just do Array::class.java
                    this
                } else {
                    rawType.notNull()
                })
                .apply {
                    for (typeArgument in typeArguments) {
                        add(", ")
                        add(typeArgument.asRuntimeType(typeVariableAccessor))
                    }
                }
                .add(")")
                .build()
        is WildcardTypeName -> when {
            inTypes.size == 1 -> inTypes[0].asRuntimeType(typeVariableAccessor)
            outTypes[0] == ANY -> ANY.asRuntimeType(typeVariableAccessor)
            else -> outTypes[0].asRuntimeType(typeVariableAccessor)
        }
        is TypeVariableName -> typeVariableAccessor(this)
        else -> CodeBlock.of("%T::class.javaObjectType", notNull())
    }
