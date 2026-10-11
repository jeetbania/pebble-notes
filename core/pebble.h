#pragma once
#ifdef __cplusplus
extern "C" {
#endif
typedef struct pebble_store pebble_store;
pebble_store *pebble_open(const char *path);
void pebble_close(pebble_store *store);
const char *pebble_error(pebble_store *store);
int pebble_put(pebble_store *store, const char *revision, int remote);
char *pebble_list(pebble_store *store, int pending_only, int heads_only);
int pebble_ack(pebble_store *store, const char *revision_id);
void pebble_free(char *text);
#ifdef __cplusplus
}
#endif
