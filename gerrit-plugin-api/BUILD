SRCS = [
  'gerrit-server/src/main/java/',
  'gerrit-httpd/src/main/java/',
  'gerrit-sshd/src/main/java/',
]

PLUGIN_API = [
  '//gerrit-httpd:httpd',
  '//gerrit-pgm:init-api',
  '//gerrit-server:server',
  '//gerrit-sshd:sshd',
]

EXPORTS = [
  '//gerrit-antlr:query_exception',
  '//gerrit-antlr:query_parser',
  '//gerrit-common:annotations',
  '//gerrit-common:server',
  '//gerrit-extension-api:api',
  '//gerrit-gwtexpui:server',
  '//gerrit-reviewdb:server',
  '//lib/commons:lang',
  '//lib/dropwizard:dropwizard-core',
  '//lib/guice:guice',
  '//lib/guice:guice-assistedinject',
  '//lib/guice:guice-servlet',
  '//lib/guice:javax-inject',
  '//lib/guice:multibindings',
  '//lib/jgit/org.eclipse.jgit.http.server:jgit-servlet',
  '//lib/jgit/org.eclipse.jgit:jgit',
  '//lib/joda:joda-time',
  '//lib/log:api',
  '//lib/log:log4j',
  '//lib/mina:sshd',
  '//lib/ow2:ow2-asm',
  '//lib/ow2:ow2-asm-analysis',
  '//lib/ow2:ow2-asm-commons',
  '//lib/ow2:ow2-asm-util',
  '//lib:args4j',
  '//lib:blame-cache',
  '//lib:guava',
  '//lib:gson',
  '//lib:gwtorm',
  '//lib:icu4j',
  '//lib:jsch',
  '//lib:mime-util',
  '//lib:protobuf',
  '//lib:servlet-api-3_1',
  '//lib:soy',
  '//lib:velocity',
]

java_binary(
  name = 'plugin-api',
  main_class = 'Dummy',
  runtime_deps = [':lib'],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'lib',
  exports = PLUGIN_API + EXPORTS,
  visibility = ['//visibility:public'],
)

java_library(
  name = 'lib-neverlink',
  neverlink = 1,
  exports = PLUGIN_API + EXPORTS,
  visibility = ['//visibility:public'],
)

java_binary(
  name = 'plugin-api-sources',
  main_class = 'Dummy',
  runtime_deps = [
    '//gerrit-antlr:libquery_exception-src.jar',
    '//gerrit-antlr:libquery_parser-src.jar',
    '//gerrit-common:libannotations-src.jar',
    '//gerrit-extension-api:libapi-src.jar',
    '//gerrit-gwtexpui:libserver-src.jar',
    '//gerrit-httpd:libhttpd-src.jar',
    '//gerrit-pgm:libinit-api-src.jar',
    '//gerrit-reviewdb:libserver-src.jar',
    '//gerrit-server:libserver-src.jar',
    '//gerrit-sshd:libsshd-src.jar',
  ],
  visibility = ['//visibility:public'],
)

load('//tools/bzl:javadoc.bzl', 'java_doc')

java_doc(
  name = 'plugin-api-javadoc',
  title = 'Gerrit Review Plugin API Documentation',
  pkgs = ['com.google.gerrit'],
  libs = PLUGIN_API + [
    '//gerrit-antlr:query_exception',
    '//gerrit-antlr:query_parser',
    '//gerrit-common:annotations',
    '//gerrit-common:server',
    '//gerrit-extension-api:api',
    '//gerrit-gwtexpui:server',
    '//gerrit-reviewdb:server',
  ],
  visibility = ['//visibility:public'],
)
