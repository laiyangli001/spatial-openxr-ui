cmake_minimum_required(VERSION 3.22.1)
project(spatial_ui_geometry LANGUAGES CXX)
# The consuming OpenXR host supplies its OpenXR header include directory.
add_library(spatial_ui_geometry INTERFACE)
target_compile_features(spatial_ui_geometry INTERFACE cxx_std_17)
target_include_directories(spatial_ui_geometry INTERFACE ${CMAKE_CURRENT_LIST_DIR}/include)
