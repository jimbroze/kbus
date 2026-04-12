package com.test.external

interface ExternalInterface

class ExternalEmpty : ExternalInterface

class ExternalNestedWithExternal(val nested: ExternalEmpty)

class ExternalNestedWithPrimitive(val nested: String)
