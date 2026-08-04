package com.jimbroze.kbus.generation.test

import com.test.external.ExternalEmpty
import com.test.external.ExternalNestedWithExternal
import com.test.external.ExternalNestedWithPrimitive

class ContainsExternalEmptySub(val externalDependency: ExternalEmpty)

class ContainsExternalNestedExternalSub(val externalDependency: ExternalNestedWithExternal)

class ContainsExternalNestedPrimitiveSub(val externalDependency: ExternalNestedWithPrimitive)
