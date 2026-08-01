// alldump.c — dump EVERY rw-p region of a process with exact sizes (64-bit), via process_vm_readv.
// usage: alldump <pid> <outdir>
#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <sys/uio.h>
#include <unistd.h>

int main(int argc, char **argv) {
    if (argc < 3) { fprintf(stderr, "usage: %s <pid> <outdir>\n", argv[0]); return 1; }
    pid_t pid = (pid_t)atoi(argv[1]);
    const char *outdir = argv[2];

    char path[256];
    snprintf(path, sizeof(path), "/proc/%d/maps", pid);
    FILE *f = fopen(path, "r");
    if (!f) { perror("maps"); return 1; }

    char line[512];
    int nreg = 0, nread = 0;
    while (fgets(line, sizeof(line), f)) {
        uint64_t start, end;
        char perms[8], rest[256];
        int n = sscanf(line, "%llx-%llx %7s %*s %*s %*s %255[^\n]", &start, &end, perms, rest);
        if (n < 3) continue;
        // only rw-p
        if (strncmp(perms, "rw-p", 4) != 0) continue;
        // skip file-backed RO-ish shared libs' rw-p? keep everything rw-p (incl ashmem/dalvik)
        uint64_t size = end - start;
        if (size == 0) continue;
        nreg++;

        char fn[512];
        snprintf(fn, sizeof(fn), "%s/dmp_%llx.bin", outdir, start);
        FILE *o = fopen(fn, "wb");
        if (!o) { fprintf(stderr, "open out %s: ", fn); perror(""); continue; }

        unsigned char *buf = malloc(1048576);
        uint64_t done = 0;
        while (done < size) {
            uint64_t chunk = (size - done > 1048576) ? 1048576 : (size - done);
            struct iovec local = { buf, (size_t)chunk };
            struct iovec remote = { (void *)(uintptr_t)(start + done), (size_t)chunk };
            ssize_t r = process_vm_readv(pid, &local, 1, &remote, 1, 0);
            if (r <= 0) break;
            fwrite(buf, 1, (size_t)r, o);
            done += (uint64_t)r;
        }
        free(buf);
        fclose(o);
        if (done > 0) {
            nread++;
            fprintf(stderr, "OK %llx-%llx read=%llu\n", start, end, done);
        } else {
            fprintf(stderr, "FAIL %llx-%llx (%s)\n", start, end, rest);
        }
    }
    fclose(f);
    fprintf(stderr, "regions=%d read=%d\n", nreg, nread);
    return 0;
}
