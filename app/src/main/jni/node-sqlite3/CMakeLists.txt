add_library(node-sqlite3 STATIC
    src/async.h src/database.cc src/database.h src/gcc-preinclude.h src/macros.h src/node_sqlite3.cc src/statement.cc src/statement.h src/threading.h)

target_include_directories(node-sqlite3 PRIVATE ../sqlite ../node ../nan)