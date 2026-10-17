package com.tagforge.domain.scan

/** What the armed operation does. Later phases add ERASE, FORMAT, CLONE, ... */
enum class OperationKind { READ, MEMORY_DUMP, WRITE }
