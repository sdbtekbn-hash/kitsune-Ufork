#pragma once

#include <cxx.h>

namespace rust {
    struct ModuleInfo;
    
    class MagiskD {
    public:
        rust::Vec<ModuleInfo> handle_modules() const noexcept;
    };
    
    MagiskD& get_magiskd_instance();
}