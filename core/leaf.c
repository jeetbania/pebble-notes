#include "leaf.h"
#include "sqlite3.h"
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

struct leaf_store { sqlite3 *db; char error[512]; };
static int fail(leaf_store *s, const char *message) { snprintf(s->error, sizeof(s->error), "%s", message); return 0; }
static int run(leaf_store *s, const char *sql) {
    char *error = NULL;
    int rc = sqlite3_exec(s->db, sql, NULL, NULL, &error);
    if (rc != SQLITE_OK) { fail(s, error ? error : sqlite3_errmsg(s->db)); sqlite3_free(error); return 0; }
    return 1;
}
leaf_store *leaf_open(const char *path) {
    leaf_store *s = calloc(1, sizeof(*s));
    if (sqlite3_open_v2(path, &s->db, SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX, NULL) != SQLITE_OK) { leaf_close(s); return NULL; }
    sqlite3_busy_timeout(s->db, 5000);
    if (!run(s, "PRAGMA journal_mode=WAL; PRAGMA synchronous=FULL; PRAGMA foreign_keys=ON; PRAGMA secure_delete=ON;"
        "CREATE TABLE IF NOT EXISTS revisions(id TEXT PRIMARY KEY,note_id TEXT NOT NULL,payload TEXT NOT NULL,uploaded INTEGER NOT NULL);"
        "CREATE TABLE IF NOT EXISTS edges(child TEXT NOT NULL,parent TEXT NOT NULL,PRIMARY KEY(child,parent),FOREIGN KEY(child) REFERENCES revisions(id));"
        "CREATE INDEX IF NOT EXISTS notes_index ON revisions(note_id);"
        "CREATE INDEX IF NOT EXISTS parents_index ON edges(parent); PRAGMA user_version=1;")) { leaf_close(s); return NULL; }
    return s;
}
void leaf_close(leaf_store *s) { if(s) { sqlite3_close(s->db); free(s); } }
const char *leaf_error(leaf_store *s) { return s ? s->error : "Could not open local database"; }
void leaf_free(char *s) { free(s); }

int leaf_put(leaf_store *s, const char *payload, int remote) {
    if (!s || !payload) return 0;
    s->error[0] = 0;
    if (strlen(payload) > 16 * 1024 * 1024) return fail(s, "Note revision exceeds 16 MB");
    sqlite3_stmt *q = NULL;
    sqlite3_prepare_v2(s->db, "SELECT json_valid(?1)", -1, &q, NULL);
    sqlite3_bind_text(q, 1, payload, -1, SQLITE_TRANSIENT);
    int valid = sqlite3_step(q) == SQLITE_ROW && sqlite3_column_int(q, 0);
    sqlite3_finalize(q);
    if (!valid) return fail(s, "Invalid JSON revision");
    const char *validation =
        "SELECT coalesce(json_extract(?1,'$.schema') IN (1,2) AND "
        "json_type(?1,'$.id')='text' AND length(json_extract(?1,'$.id')) BETWEEN 1 AND 128 AND "
        "json_type(?1,'$.noteId')='text' AND length(json_extract(?1,'$.noteId')) BETWEEN 1 AND 128 AND "
        "json_type(?1,'$.deviceId')='text' AND json_type(?1,'$.createdAt')='integer' AND "
        "json_type(?1,'$.parents')='array' AND json_array_length(?1,'$.parents')<=64 AND "
        "json_type(?1,'$.note.title')='text' AND json_type(?1,'$.note.text')='text' AND "
        "json_type(?1,'$.note.collection')='text' AND json_type(?1,'$.note.spans')='array' AND "
        "json_type(?1,'$.note.attachments')='array' AND "
        "(json_type(?1,'$.note.textScale') IS NULL OR (json_type(?1,'$.note.textScale') IN ('integer','real') AND json_extract(?1,'$.note.textScale') BETWEEN 0.8 AND 1.6)) AND "
        "(json_type(?1,'$.note.task') IS NULL OR (json_type(?1,'$.note.task')='object' AND "
        "json_type(?1,'$.note.task.dueAt')='integer' AND json_extract(?1,'$.note.task.dueAt') BETWEEN 0 AND 32503680000000 AND "
        "json_type(?1,'$.note.task.hasTime') IN ('true','false') AND json_type(?1,'$.note.task.completed') IN ('true','false') AND json_type(?1,'$.note.task.remind') IN ('true','false') AND "
        "json_type(?1,'$.note.task.priority')='integer' AND json_extract(?1,'$.note.task.priority') BETWEEN 0 AND 3 AND "
        "json_type(?1,'$.note.task.list')='text' AND length(json_extract(?1,'$.note.task.list')) BETWEEN 1 AND 128 AND "
        "(json_type(?1,'$.note.task.status') IS NULL OR json_extract(?1,'$.note.task.status') IN ('todo','progress','review','done')) AND "
        "json_extract(?1,'$.note.task.repeatRule') IN ('none','daily','weekly','monthly'))) AND "
        "json_type(?1,'$.note.pinned') IN ('true','false') AND "
        "json_type(?1,'$.note.archived') IN ('true','false') AND "
        "json_type(?1,'$.note.deleted') IN ('true','false') AND "
        "(json_type(?1,'$.note.deletedAt') IS NULL OR (json_type(?1,'$.note.deletedAt')='integer' AND json_extract(?1,'$.note.deletedAt')>0)) AND "
        "(json_type(?1,'$.note.purgedAt') IS NULL OR (json_type(?1,'$.note.purgedAt')='integer' AND json_extract(?1,'$.note.purgedAt')>0 AND json_extract(?1,'$.note.deleted')=1 AND json_extract(?1,'$.note.title')='' AND json_extract(?1,'$.note.text')='' AND json_array_length(?1,'$.note.attachments')=0 AND json_array_length(?1,'$.note.blocks')=0 AND json_array_length(?1,'$.parents')=0 AND json_array_length(?1,'$.note.spans')=0)) AND "
        "(json_extract(?1,'$.schema')=1 OR ( "
        "json_type(?1,'$.note.blocks')='array' AND json_array_length(?1,'$.note.blocks')<=2000 AND "
        "json_extract(?1,'$.note.recordType') IN ('note','folder') AND "
        "json_type(?1,'$.note.tags')='array' AND json_array_length(?1,'$.note.tags')<=64 AND "
        "json_type(?1,'$.note.folderEmoji')='text' AND json_type(?1,'$.note.folderImage')='text' AND "
        "NOT EXISTS(SELECT 1 FROM json_each(?1,'$.note.tags') WHERE type<>'text' OR length(value)>64) AND "
        "NOT EXISTS(SELECT 1 FROM json_each(?1,'$.note.blocks') GROUP BY json_extract(value,'$.id') HAVING count(*)>1) AND "
        "NOT EXISTS(SELECT 1 FROM json_each(?1,'$.note.blocks') AS b WHERE NOT coalesce( "
        "json_type(b.value,'$.id')='text' AND length(json_extract(b.value,'$.id')) BETWEEN 1 AND 128 AND "
        "json_extract(b.value,'$.kind') IN ('text','bullet','number','check','toggle','divider','image','file','table') AND "
        "(json_type(b.value,'$.collapsed') IS NULL OR json_type(b.value,'$.collapsed') IN ('true','false')) AND "
        "(json_type(b.value,'$.parentId') IS NULL OR (json_type(b.value,'$.parentId')='text' AND EXISTS(SELECT 1 FROM json_each(?1,'$.note.blocks') AS p WHERE p.key<b.key AND json_extract(p.value,'$.id')=json_extract(b.value,'$.parentId') AND json_extract(p.value,'$.kind')='toggle'))) AND "
        "json_type(b.value,'$.text')='text' AND json_type(b.value,'$.caption')='text' AND "
        "json_type(b.value,'$.checked') IN ('true','false') AND json_type(b.value,'$.indent')='integer' AND json_extract(b.value,'$.indent') BETWEEN 0 AND 8 AND "
        "json_extract(b.value,'$.presentation') IN ('large','small','grid') AND json_type(b.value,'$.mediaId')='text' AND "
        "(json_extract(b.value,'$.kind') NOT IN ('image','file') OR EXISTS(SELECT 1 FROM json_each(?1,'$.note.attachments') AS a WHERE json_extract(a.value,'$.id')=json_extract(b.value,'$.mediaId'))) AND "
        "json_type(b.value,'$.spans')='array' AND "
        "NOT EXISTS(SELECT 1 FROM json_each(b.value,'$.spans') AS t WHERE NOT coalesce(json_type(t.value,'$.start')='integer' AND json_type(t.value,'$.length')='integer' AND json_extract(t.value,'$.start')>=0 AND json_extract(t.value,'$.length')>=0 AND (json_extract(t.value,'$.kind') IN ('bold','italic','strike','underline','title','subtitle','headline','highlight') OR json_extract(t.value,'$.kind') LIKE 'link:%'),0)) AND "
        "json_type(b.value,'$.cells')='array' AND json_array_length(b.value,'$.cells')<=100 AND "
        "(json_extract(b.value,'$.kind')<>'table' OR (json_array_length(b.value,'$.cells')>=1 AND json_array_length(b.value,'$.cells[0]') BETWEEN 1 AND 12)) AND "
        "NOT EXISTS(SELECT 1 FROM json_each(b.value,'$.cells') AS row WHERE row.type<>'array' OR json_array_length(row.value)<>json_array_length(b.value,'$.cells[0]') OR EXISTS(SELECT 1 FROM json_each(row.value) AS cell WHERE cell.type<>'text')) "
        ",0)))) AND "
        "NOT EXISTS(SELECT 1 FROM json_each(?1,'$.parents') WHERE type<>'text' OR value=json_extract(?1,'$.id')) AND "
        "NOT EXISTS(SELECT 1 FROM json_each(?1,'$.note.spans') WHERE NOT coalesce("
        "json_type(value,'$.start')='integer' AND json_type(value,'$.length')='integer' AND "
        "json_extract(value,'$.start')>=0 AND json_extract(value,'$.length')>=0 AND (json_extract(value,'$.kind') IN ('bold','italic','strike','underline','title','subtitle','headline','highlight') OR json_extract(value,'$.kind') LIKE 'link:%'),0)) AND "
        "NOT EXISTS(SELECT 1 FROM json_each(?1,'$.note.attachments') WHERE NOT coalesce("
        "json_type(value,'$.id')='text' AND length(json_extract(value,'$.id'))=64 AND "
        "json_extract(value,'$.id') NOT GLOB '*[^a-f0-9]*' AND json_type(value,'$.name')='text' AND json_type(value,'$.mime')='text',0)),0)";
    sqlite3_prepare_v2(s->db, validation, -1, &q, NULL);
    sqlite3_bind_text(q, 1, payload, -1, SQLITE_TRANSIENT);
    valid = sqlite3_step(q) == SQLITE_ROW && sqlite3_column_int(q, 0);
    sqlite3_finalize(q);
    if (!valid) return fail(s, "Unsupported or incomplete revision format");
    if (!run(s, "BEGIN IMMEDIATE")) return 0;
    sqlite3_prepare_v2(s->db, "SELECT payload FROM revisions WHERE id=json_extract(?1,'$.id')", -1, &q, NULL);
    sqlite3_bind_text(q, 1, payload, -1, SQLITE_TRANSIENT);
    int exists = sqlite3_step(q) == SQLITE_ROW;
    sqlite3_finalize(q);
    if (exists) {
        // Compare semantic JSON, including array positions, rather than serialization order.
        sqlite3_prepare_v2(s->db, "SELECT NOT EXISTS(SELECT fullkey,type,atom FROM json_tree(?1) EXCEPT SELECT fullkey,type,atom FROM json_tree((SELECT payload FROM revisions WHERE id=json_extract(?1,'$.id')))) AND NOT EXISTS(SELECT fullkey,type,atom FROM json_tree((SELECT payload FROM revisions WHERE id=json_extract(?1,'$.id'))) EXCEPT SELECT fullkey,type,atom FROM json_tree(?1))",-1,&q,NULL);
        sqlite3_bind_text(q,1,payload,-1,SQLITE_TRANSIENT); int equal=sqlite3_step(q)==SQLITE_ROW && sqlite3_column_int(q,0); sqlite3_finalize(q);
        run(s,"ROLLBACK"); return equal ? 1 : fail(s,"Revision ID collision: existing content is immutable");
    }
    // A retained empty tombstone prevents offline devices and old backups from resurrecting erased content.
    sqlite3_prepare_v2(s->db,"SELECT 1 FROM revisions WHERE note_id=json_extract(?1,'$.noteId') AND json_extract(payload,'$.note.purgedAt')>0 LIMIT 1",-1,&q,NULL);
    sqlite3_bind_text(q,1,payload,-1,SQLITE_TRANSIENT); int erased=sqlite3_step(q)==SQLITE_ROW; sqlite3_finalize(q);
    if(erased) { run(s,"ROLLBACK"); return 1; }
    sqlite3_prepare_v2(s->db,"SELECT coalesce(json_extract(?1,'$.note.purgedAt'),0)",-1,&q,NULL);
    sqlite3_bind_text(q,1,payload,-1,SQLITE_TRANSIENT); int purge=sqlite3_step(q)==SQLITE_ROW && sqlite3_column_int64(q,0)>0; sqlite3_finalize(q);
    if(purge) {
        const char *commands[]={"DELETE FROM edges WHERE child IN (SELECT id FROM revisions WHERE note_id=json_extract(?1,'$.noteId'))", "DELETE FROM revisions WHERE note_id=json_extract(?1,'$.noteId')"};
        for(int i=0;i<2;i++) { sqlite3_prepare_v2(s->db,commands[i],-1,&q,NULL); sqlite3_bind_text(q,1,payload,-1,SQLITE_TRANSIENT); int result=sqlite3_step(q); sqlite3_finalize(q); if(result!=SQLITE_DONE) {run(s,"ROLLBACK");return fail(s,sqlite3_errmsg(s->db));} }
    }
    // Parents may arrive later, but known graph links must belong to the same note.
    sqlite3_prepare_v2(s->db,
        "SELECT 1 FROM revisions WHERE id IN (SELECT value FROM json_each(?1,'$.parents')) AND note_id<>json_extract(?1,'$.noteId') "
        "UNION SELECT 1 FROM edges JOIN revisions ON edges.child=revisions.id WHERE edges.parent=json_extract(?1,'$.id') AND revisions.note_id<>json_extract(?1,'$.noteId') LIMIT 1", -1,&q,NULL);
    sqlite3_bind_text(q,1,payload,-1,SQLITE_TRANSIENT);
    valid = sqlite3_step(q) != SQLITE_ROW; sqlite3_finalize(q);
    if(!valid) { run(s,"ROLLBACK"); return fail(s,"Parent revision belongs to another note"); }
    sqlite3_prepare_v2(s->db,
        "INSERT INTO revisions VALUES(json_extract(?1,'$.id'),json_extract(?1,'$.noteId'),?1,?2)",-1,&q,NULL);
    sqlite3_bind_text(q,1,payload,-1,SQLITE_TRANSIENT); sqlite3_bind_int(q,2,remote?1:0);
    int rc=sqlite3_step(q); sqlite3_finalize(q);
    if(rc!=SQLITE_DONE) { run(s,"ROLLBACK"); return fail(s,sqlite3_errmsg(s->db)); }
    sqlite3_prepare_v2(s->db,
        "INSERT OR IGNORE INTO edges SELECT json_extract(?1,'$.id'),value FROM json_each(?1,'$.parents')",-1,&q,NULL);
    sqlite3_bind_text(q,1,payload,-1,SQLITE_TRANSIENT); rc=sqlite3_step(q); sqlite3_finalize(q);
    if(rc!=SQLITE_DONE) { run(s,"ROLLBACK"); return fail(s,sqlite3_errmsg(s->db)); }
    sqlite3_prepare_v2(s->db,
        "WITH RECURSIVE ancestors(id) AS (SELECT parent FROM edges WHERE child=json_extract(?1,'$.id') UNION SELECT e.parent FROM edges e JOIN ancestors a ON e.child=a.id) SELECT 1 FROM ancestors WHERE id=json_extract(?1,'$.id') LIMIT 1",-1,&q,NULL);
    sqlite3_bind_text(q,1,payload,-1,SQLITE_TRANSIENT); valid=sqlite3_step(q)!=SQLITE_ROW; sqlite3_finalize(q);
    if(!valid) { run(s,"ROLLBACK"); return fail(s,"Revision graph contains a cycle"); }
    return run(s,"COMMIT");
}
char *leaf_list(leaf_store *s,int pending,int heads) {
    sqlite3_stmt *q=NULL;
    const char *sql = heads ?
        "SELECT coalesce(json_group_array(json(payload)),'[]') FROM (SELECT payload FROM revisions r WHERE NOT EXISTS(SELECT 1 FROM edges WHERE parent=r.id) ORDER BY json_extract(payload,'$.createdAt') DESC,id DESC)" :
        pending ? "SELECT coalesce(json_group_array(json(payload)),'[]') FROM (SELECT payload FROM revisions WHERE uploaded=0 ORDER BY rowid)" :
        "SELECT coalesce(json_group_array(json(payload)),'[]') FROM (SELECT payload FROM revisions ORDER BY rowid)";
    if(sqlite3_prepare_v2(s->db,sql,-1,&q,NULL)!=SQLITE_OK) { fail(s,sqlite3_errmsg(s->db)); return NULL; }
    char *result=NULL;
    if(sqlite3_step(q)==SQLITE_ROW) result=strdup((const char*)sqlite3_column_text(q,0));
    sqlite3_finalize(q); return result;
}
int leaf_ack(leaf_store *s,const char *id) {
    sqlite3_stmt *q=NULL; sqlite3_prepare_v2(s->db,"UPDATE revisions SET uploaded=1 WHERE id=?1",-1,&q,NULL);
    sqlite3_bind_text(q,1,id,-1,SQLITE_TRANSIENT); int rc=sqlite3_step(q); sqlite3_finalize(q);
    return rc==SQLITE_DONE ? 1 : fail(s,sqlite3_errmsg(s->db));
}
