package com.jarves.mh.runtime

import org.junit.Assert.*
import org.junit.Test

class TerminalCommandPolicyTest {
    @Test fun onlySimplePackageCommandsMayBeRewritten() {
        assertTrue(isSimplePackageCommand("sudo apt install git"))
        assertTrue(isSimplePackageCommand("apt-get update"))
        assertFalse(isSimplePackageCommand("echo 'apt install git'"))
        assertFalse(isSimplePackageCommand("cat <<'EOF'\napt install git\nEOF"))
        assertFalse(isSimplePackageCommand("apt install git && echo done"))
        assertFalse(isSimplePackageCommand("apt install \$PACKAGE"))
        assertFalse(isSimplePackageCommand("python3 main.py"))
    }
}
