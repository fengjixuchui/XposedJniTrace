//
// Created by zhenxi on 2022/1/21.
//
#include <iosfwd>
#include <iostream>
#include <string>
#include <map>
#include <list>
#include <jni.h>
#include <dlfcn.h>
#include <cstddef>
#include <fcntl.h>
#include <dirent.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <sstream>
#include <ostream>

#include <cstdlib>
#include <sys/ptrace.h>
#include <sys/stat.h>
#include <syscall.h>
#include <climits>
#include <sys/socket.h>
#include <sys/wait.h>
#include <sys/user.h>
#include <pthread.h>
#include <vector>
#include <zlib.h>
#include <list>
#include <string>
#include <map>
#include <list>

#include <cstring>
#include <cstdio>
#include <regex>
#include <cerrno>
#include <climits>
#include <iostream>
#include <fstream>

#include "linkerHandler.h"
#include "linker_callback.h"
#include "logging.h"
#include "libpath.h"
#include "HookUtils.h"
#include "appUtils.h"
#include "hack.h"
#include "main.h"
#include "fileUtils.h"
#include "mylibc.h"
#include "elf_util.h"
#include "il2cpp_dump.h"


#define MATCH_ELF "libil2cpp.so"

using namespace ZhenxiRunTime;

class FunJniLinkerCallBack : public LinkerLoadCallBack {
public:
    void loadBefore(const char *path) const override {

    }

    void loadAfter(const char *path, const char *redirect_path, void *ret) const override {
        if (path == nullptr) {
            return;
        }
        if (StringUtils::endsWith(path, MATCH_ELF)) {
            LOGE(">>>>>>>>>>>> find libil2cpp.so is load [%s] %s",saveFilePath, path)
            hack_prepare(path, saveFilePath);
        }
    }
};
void linkerCallBack::hookLinkerCallBack() {
    ZhenxiRunTime::linkerHandler::init();
    ZhenxiRunTime::linkerHandler::addLinkerCallBack(new FunJniLinkerCallBack());
}
