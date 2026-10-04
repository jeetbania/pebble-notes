#pragma once
#ifdef __cplusplus
extern "C" {
#endif
typedef struct leaf_store leaf_store;
leaf_store *leaf_open(const char *path);
void leaf_close(leaf_store *store);
const char *leaf_error(leaf_store *store);
int leaf_put(leaf_store *store, const char *revision, int remote);
char *leaf_list(leaf_store *store, int pending_only, int heads_only);
int leaf_ack(leaf_store *store, const char *revision_id);
void leaf_free(char *text);
#ifdef __cplusplus
}
#endif
