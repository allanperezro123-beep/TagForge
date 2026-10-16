package com.tagforge.domain.scan

/** What the armed operation does. Later phases add WRITE, ERASE, FORMAT, CLONE, ... */
enum class OperationKind { READ, MEMORY_DUMP }
