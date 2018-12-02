# Sets the minimum version of CMake required to build the native
# library. You should either keep the default value or only pass a
# value of 3.4.0 or lower.

cmake_minimum_required(VERSION 3.4.1)

# store the path to the sodium library in a variable
set(sodium-dir ${CMAKE_CURRENT_SOURCE_DIR}/../sodium)

add_library(sodium SHARED IMPORTED)
set_property(	TARGET sodium
				PROPERTY IMPORTED_LOCATION
						# Provides the path to the appropriate libsodium.so
						${sodium-dir}/lib/${ANDROID_ABI}/libsodium.so)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds it for you.
# Gradle automatically packages shared libraries with your APK.

add_library( # Sets the name of the library.
             native-lib

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             # Associated headers in the same location as their source
             # file are automatically included.
             src/main/cpp/sodium.c)

target_include_directories(native-lib PRIVATE
							${sodium-dir}/include)

target_link_libraries(native-lib android sodium log)

