// vmread.c — dump another process's memory via process_vm_readv (root, bypasses dumpable)
// usage: vmread <pid> <start_hex> <size_dec> <outfile>
#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <sys/uio.h>
#include <unistd.h>

int main(int argc, char **argv) {
    if (argc < 5) {
        fprintf(stderr, "usage: %s <pid> <start_hex> <size_dec> <outfile>\n", argv[0]);
        return 1;
    }
    pid_t pid = (pid_t)atoi(argv[1]);
    uint64_t start = strtoull(argv[2], NULL, 16);
    size_t size = (size_t)strtoull(argv[3], NULL, 10);
    if (size == 0 || size > 300 * 1024 * 1024) { fprintf(stderr, "bad size\n"); return 1; }

    unsigned char *buf = malloc(size);
    if (!buf) { perror("malloc"); return 1; }
    memset(buf, 0, size);

    // read in 1MB chunks to handle partial failures
    size_t done = 0;
    while (done < size) {
        size_t chunk = (size - done > 1048576) ? 1048576 : (size - done);
        struct iovec local = { buf + done, chunk };
        struct iovec remote = { (void *)(uintptr_t)(start + done), chunk };
        ssize_t n = process_vm_readv(pid, &local, 1, &remote, 1, 0);
        if (n < 0) { perror("process_vm_readv"); break; }
        if (n == 0) break;
        done += (size_t)n;
    }

    FILE *f = fopen(argv[4], "wb");
    if (!f) { perror("fopen"); return 1; }
    fwrite(buf, 1, done, f);
    fclose(f);
    fprintf(stderr, "read %zu bytes\n", done);
    return 0;
}
