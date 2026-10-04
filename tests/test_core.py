"""Exercise the production C database used by both apps, not a substitute model."""
import ctypes, json, tempfile, pathlib, unittest, subprocess, uuid, random
ROOT = pathlib.Path(__file__).resolve().parents[1]
BUILD = ROOT.parents[1] / "work" / "tests"
BUILD.mkdir(parents=True, exist_ok=True)
subprocess.run(["clang", "-O2", "-shared", "-DSQLITE_OMIT_LOAD_EXTENSION", "-I" + str(ROOT / "core/vendor"), str(ROOT / "core/leaf.c"), str(ROOT / "core/vendor/sqlite3.c"), "-o", str(BUILD / "libleaf.dylib")], check=True)
lib = ctypes.CDLL(str(BUILD / "libleaf.dylib"))
lib.leaf_open.argtypes = [ctypes.c_char_p]; lib.leaf_open.restype = ctypes.c_void_p
lib.leaf_close.argtypes = [ctypes.c_void_p]
lib.leaf_put.argtypes = [ctypes.c_void_p, ctypes.c_char_p, ctypes.c_int]; lib.leaf_put.restype = ctypes.c_int
lib.leaf_list.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_int]; lib.leaf_list.restype = ctypes.c_void_p
lib.leaf_error.argtypes = [ctypes.c_void_p]; lib.leaf_error.restype = ctypes.c_char_p
lib.leaf_free.argtypes = [ctypes.c_void_p]
lib.leaf_ack.argtypes = [ctypes.c_void_p, ctypes.c_char_p]
def revision(id=None, parents=(), text="Hello", note_id="note-1", deleted=False):
    return dict(schema=1,id=id or str(uuid.uuid4()),noteId=note_id,deviceId="device",parents=list(parents),createdAt=1000,note=dict(title="Thoughts",text=text,spans=[],attachments=[],collection="Personal",pinned=False,archived=False,deleted=deleted))
class Store:
    def __init__(self,path=":memory:"): self.handle=lib.leaf_open(str(path).encode()); assert self.handle
    def put(self,r,remote=False): return bool(lib.leaf_put(self.handle,json.dumps(r,ensure_ascii=False).encode(),remote))
    def list(self,pending=False,heads=False):
        ptr=lib.leaf_list(self.handle,pending,heads)
        assert ptr,lib.leaf_error(self.handle)
        try: return json.loads(ctypes.string_at(ptr))
        finally: lib.leaf_free(ptr)
    def close(self): lib.leaf_close(self.handle)
class CoreTests(unittest.TestCase):
    def setUp(self): self.s=Store()
    def tearDown(self): self.s.close()
    def test_permanent_deletion_blocks_old_backups_and_offline_edits(self):
        original=revision('secret'); original['note']['attachments']=[dict(id='a'*64,name='secret.png',mime='image/png')]
        self.assertTrue(self.s.put(original));self.assertTrue(self.s.put(revision('trash',['secret'],deleted=True)))
        marker=revision('purge',text='',deleted=True);marker['note'].update(title='',attachments=[],blocks=[],purgedAt=10000,deletedAt=1000)
        self.assertTrue(self.s.put(marker));self.assertEqual(self.s.list(),[marker])
        for old in [original,revision('offline',['secret'],'Old copy'),revision('restore',['trash'],'Restored copy')]:self.assertTrue(self.s.put(old,True));self.assertEqual(self.s.list(),[marker])
        self.assertEqual(self.s.list(pending=True),[marker])
        invalid=revision('bad-purge');invalid['note']['purgedAt']=1000;self.assertFalse(self.s.put(invalid))
    def test_toggle_tree_validates_order_and_parent_kind(self):
        def block(id,kind='text',**kw):return dict(id=id,kind=kind,text='',spans=[],checked=False,indent=0,mediaId='',caption='',presentation='large',cells=[],**kw)
        r=revision('toggle');r.update(schema=2);r['note'].update(blocks=[block('group','toggle',collapsed=False),block('child',parentId='group'),block('nested','toggle',parentId='group'),block('leaf','check',parentId='nested')],tags=[],recordType='note',folderEmoji='',folderImage='')
        self.assertTrue(self.s.put(r));self.assertEqual(self.s.list(),[r])
        for parent in ['missing','child','leaf']:
            bad=json.loads(json.dumps(r));bad['id']='bad-'+parent;bad['note']['blocks'][1]['parentId']=parent;self.assertFalse(self.s.put(bad))
        bad=json.loads(json.dumps(r));bad['id']='cycle';bad['note']['blocks'][0]['parentId']='nested';self.assertFalse(self.s.put(bad))
    def test_task_round_trip_and_validation(self):
        task=dict(dueAt=1791091800000,hasTime=True,priority=3,repeatRule="weekly",list="Work",completed=False,remind=True)
        r=revision();r['note']['task']=task
        self.assertTrue(self.s.put(r));self.assertIn(r,self.s.list())
        for key,value in [('dueAt',-1),('dueAt','tomorrow'),('priority',4),('priority',True),('repeatRule','hourly'),('completed',1),('hasTime',None),('list',''),('remind','yes'),('status','unknown'),('status',5)]:
            invalid=revision();invalid['note']['task']=dict(task,**{key:value});self.assertFalse(self.s.put(invalid),(key,value))
        staged=revision();staged['note']['task']=dict(task,status='review');self.assertTrue(self.s.put(staged));self.assertIn(staged,self.s.list())
        invalid=revision();invalid['note']['task']=None;self.assertFalse(self.s.put(invalid))
    def test_text_scale_round_trip_and_validation(self):
        for value in (.8, 1, 1.2, 1.6):
            r=revision(); r['note']['textScale']=value
            self.assertTrue(self.s.put(r)); self.assertIn(r,self.s.list())
        before=self.s.list()
        for value in ('large', None, 0, 1.7, True):
            r=revision(); r['note']['textScale']=value
            self.assertFalse(self.s.put(r))
        self.assertEqual(self.s.list(),before)
    def test_restart_and_pending_queue(self):
        with tempfile.TemporaryDirectory() as d:
            s=Store(pathlib.Path(d)/"notes.sqlite"); r=revision(); self.assertTrue(s.put(r)); s.close()
            s=Store(pathlib.Path(d)/"notes.sqlite"); self.assertEqual(s.list(pending=True),[r]); lib.leaf_ack(s.handle,r['id'].encode()); s.close()
            s=Store(pathlib.Path(d)/"notes.sqlite"); self.assertEqual(s.list(pending=True),[]); self.assertEqual(s.list(),[r]); s.close()
    def test_both_offline_versions_survive_and_converge(self):
        phone=Store()
        try:
            base=revision('base'); self.s.put(base); phone.put(base,True)
            a=revision('mac',['base'],'Mac edit'); b=revision('phone',['base'],'Phone edit')
            self.s.put(a); phone.put(b)
            self.s.put(b,True); phone.put(a,True)
            self.assertEqual({r['id'] for r in self.s.list(heads=True)},{'mac','phone'})
            self.assertEqual(self.s.list(heads=True),phone.list(heads=True))
            merged=revision('resolved',['mac','phone'],'Chosen version'); self.s.put(merged); phone.put(merged,True)
            self.assertEqual(self.s.list(heads=True),[merged]); self.assertEqual(len(self.s.list()),4)
        finally: phone.close()
    def test_delivery_in_random_order(self):
        records=[revision('r0')]+[revision(f'r{i}',[f'r{i-1}']) for i in range(1,100)]
        random.Random(14).shuffle(records)
        for r in records: self.assertTrue(self.s.put(r,True))
        self.assertEqual([r['id'] for r in self.s.list(heads=True)],['r99'])
    def test_deleted_vs_edited_retains_both(self):
        self.s.put(revision('base')); self.s.put(revision('delete',['base'],deleted=True)); self.s.put(revision('edit',['base'],'Work while offline'))
        self.assertEqual({r['id'] for r in self.s.list(heads=True)},{'delete','edit'})
    def test_revision_is_immutable(self):
        a=revision('same'); self.assertTrue(self.s.put(a)); b=revision('same',text='Overwrite'); self.assertFalse(self.s.put(b)); self.assertEqual(self.s.list(),[a])
    def test_semantic_duplicate_serialization(self):
        a=revision('same'); self.s.put(a)
        reordered=dict(reversed(list(a.items()))); self.assertTrue(self.s.put(reordered,True)); self.assertEqual(len(self.s.list()),1)
        self.assertEqual(len(self.s.list(pending=True)),1) # Remote echo cannot erase an unacknowledged local upload.
    def test_cycles_roll_back_atomically(self):
        self.assertTrue(self.s.put(revision('a',['b'])))
        self.assertFalse(self.s.put(revision('b',['a'])))
        self.assertEqual([r['id'] for r in self.s.list()],['a'])
    def test_cross_note_parent_rejected_in_both_orders(self):
        self.s.put(revision('a',note_id='A')); self.assertFalse(self.s.put(revision('b',['a'],note_id='B')))
        self.assertTrue(self.s.put(revision('c',['missing'],note_id='C')))
        self.assertFalse(self.s.put(revision('missing',note_id='D')))
    def test_unicode_and_image_metadata_round_trip(self):
        r=revision(text='Hello 👋 नमस्ते 日本語'); r['note']['spans']=[dict(start=6,length=2,kind='bold')]; r['note']['attachments']=[dict(id='a'*64,name='Family 👨‍👩‍👧.jpg',mime='image/jpeg')]
        self.assertTrue(self.s.put(r)); self.assertEqual(self.s.list(),[r])
    def test_unsupported_format_and_missing_fields_rejected(self):
        r=revision(); r['schema']=2; self.assertFalse(self.s.put(r))
        r=revision(); del r['note']['text']; self.assertFalse(self.s.put(r))
        r=revision(); r['note']['spans']=[{}]; self.assertFalse(self.s.put(r))
        r=revision(); r['note']['attachments']=[{}]; self.assertFalse(self.s.put(r))
        self.assertEqual(self.s.list(),[])
    def test_attachment_path_traversal_rejected(self):
        r=revision(); r['note']['attachments']=[dict(id='../outside',name='x',mime='image/png')]; self.assertFalse(self.s.put(r))
    def test_block_documents_and_folder_metadata_round_trip(self):
        r=revision('blocks'); r['schema']=2
        def block(kind='text', **kw):
            return dict(id=str(uuid.uuid4()),kind=kind,text='',spans=[],checked=False,indent=0,mediaId='',caption='',presentation='large',cells=[],**kw)
        r['note'].update(blocks=[block(),block('check'),block('table')],tags=['work'],recordType='note',folderEmoji='',folderImage='')
        r['note']['blocks'][1].update(text='Milk',checked=True,indent=2)
        r['note']['blocks'][2]['cells']=[['Item','Qty'],['Milk','2']]
        self.assertTrue(self.s.put(r)); self.assertEqual(self.s.list(),[r])
        folder=json.loads(json.dumps(r));folder.update(id='folder',noteId='folder-note',parents=[])
        folder['note'].update(recordType='folder',collection='Work/Ideas',blocks=[],folderEmoji='🌿')
        self.assertTrue(self.s.put(folder));self.assertEqual(len(self.s.list()),2)
    def test_invalid_blocks_are_rejected_atomically(self):
        r=revision('blocks');r['schema']=2
        b=dict(id='body',kind='table',text='',spans=[],checked=False,indent=0,mediaId='',caption='',presentation='large',cells=[['a','b'],['c']])
        r['note'].update(blocks=[b],tags=[],recordType='note',folderEmoji='',folderImage='')
        self.assertFalse(self.s.put(r))
        b['cells']=[['a']];r['note']['blocks']=[b,b];self.assertFalse(self.s.put(r))
        r['note']['blocks']=[b];b.update(kind='image',mediaId='a'*64);self.assertFalse(self.s.put(r))
        r['note']['attachments']=[dict(id='a'*64,name='a.png',mime='image/png')];self.assertTrue(self.s.put(r))
        malformed=json.loads(json.dumps(r));malformed['id']='bad';malformed['note']['blocks'][0]['indent']=-1;self.assertFalse(self.s.put(malformed));self.assertEqual(self.s.list(),[r])
    def test_schema_upgrade_preserves_legacy_history(self):
        old=revision('old');self.assertTrue(self.s.put(old));new=revision('new',['old']);new['schema']=2
        new['note'].update(blocks=[dict(id='body',kind='text',text='Hello',spans=[],checked=False,indent=0,mediaId='',caption='',presentation='large',cells=[])],tags=[],recordType='note',folderEmoji='',folderImage='')
        self.assertTrue(self.s.put(new));self.assertEqual({r['id'] for r in self.s.list()},{'old','new'});self.assertEqual(self.s.list(heads=True),[new]);self.assertIn(old,self.s.list())
    def test_malformed_json_does_not_damage_library(self):
        r=revision(); self.s.put(r); self.assertEqual(lib.leaf_put(self.s.handle,b'{broken',0),0); self.assertEqual(self.s.list(),[r])
if __name__=='__main__': unittest.main(verbosity=2)
