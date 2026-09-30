from conan import ConanFile


class PacmonConanFixture(ConanFile):
    requires = "zlib/1.3.1"
    tool_requires = "cmake/3.30.1"
