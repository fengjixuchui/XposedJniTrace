//
// Created by Perfare on 2020/7/4.
//

#ifndef ZYGISK_IL2CPPDUMPER_IL2CPP_DUMP_H
#define ZYGISK_IL2CPPDUMPER_IL2CPP_DUMP_H

#include "elf_util.h"

void il2cpp_api_init(void *handle);

void il2cpp_dump(const char *outDir);
/**
 * hook全部il2cpp方法,实现监听
 */
void il2cpp_tracer(const char *outDir);
/**
 * 通过hook il2cpp_runtime_invoke方法实现,il2cpp的监听
 */
void hook_invoke(void* handle,const char *outDir);

void init_il2cpp_api(void *handle);
#endif //ZYGISK_IL2CPPDUMPER_IL2CPP_DUMP_H
