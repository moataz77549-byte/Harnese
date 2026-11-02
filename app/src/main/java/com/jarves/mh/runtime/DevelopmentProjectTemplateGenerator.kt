package com.jarves.mh.runtime

import java.io.File

/** Dependency-free starters use the embedded global TypeScript compiler and Node runtime. */
object DevelopmentProjectTemplateGenerator {
    fun generate(root: File, typescript: Boolean) {
        val files = if (typescript) mapOf(
            "package.json" to """{"name":"nova-typescript-project","private":true,"scripts":{"build":"tsc -p tsconfig.json","start":"node dist/main.js","test":"npm run build && node dist/main.js"}}""",
            "tsconfig.json" to """{"compilerOptions":{"target":"ES2020","module":"CommonJS","strict":true,"outDir":"dist"},"include":["src/**/*.ts"]}""",
            "src/main.ts" to "const result: number = 6 * 7;\nconsole.log(result);\n",
        ) else mapOf(
            "package.json" to """{"name":"nova-node-project","private":true,"scripts":{"start":"node src/main.js","test":"node --test"}}""",
            "src/main.js" to "console.log(6 * 7);\n",
            "test/main.test.js" to "const test = require('node:test');\nconst assert = require('node:assert/strict');\ntest('runtime', () => assert.equal(6 * 7, 42));\n",
        )
        require(files.keys.none { File(root,it).exists() }) { "Template would overwrite an existing file" }
        files.forEach { (path,content) -> File(root,path).apply { parentFile.mkdirs();writeText(content + "\n") } }
    }
}
