# Нативная библиотека LightGBM

Колесо lightgbm ставится ради одного файла - `lib_lightgbm`, который FFM грузит
через `dlopen`. Python здесь не исполняется, зависимости колеса не ставятся.

Библиотека оказывается по пути
`.venv/lib/python3.12/site-packages/lightgbm/lib/lib_lightgbm.{dylib,so}`;
`make print-lib` печатает его, `make version` - версию.

macOS: `lib_lightgbm.dylib` ссылается на `@rpath/libomp.dylib` и несёт rpath на
префиксы Homebrew и MacPorts, поэтому нужен `brew install libomp`. Linux:
`libgomp.so.1` из дистрибутива.
