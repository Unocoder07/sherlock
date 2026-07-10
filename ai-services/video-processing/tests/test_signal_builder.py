from app.domain import signal_builder as sb


def test_no_face_emits_single_absent_signal():
    det = sb.Detection(face_count=0, detection_conf=0.0, quality=0.0, lip_activity=0.0, embedding_ref="")
    out = sb.build_signals(det, captured_at_ms=1000)
    assert len(out) == 1
    assert out[0].type == sb.FACE_ABSENT
    assert out[0].embedding_ref == ""
    assert out[0].window_start_ms == 1000


def test_single_face_emits_present_with_embedding():
    det = sb.Detection(
        face_count=1, detection_conf=0.97, quality=0.8, lip_activity=0.42,
        embedding_ref="s3://sherlock-embeddings/m/p/3.npy",
    )
    out = sb.build_signals(det, captured_at_ms=2000)
    assert len(out) == 1
    s = out[0]
    assert s.type == sb.FACE_PRESENT
    assert s.embedding_ref.endswith("3.npy")
    assert s.lip_activity == 0.42
    assert s.detection_conf == 0.97


def test_multiple_faces_adds_multiple_signal():
    det = sb.Detection(
        face_count=3, detection_conf=0.9, quality=0.7, lip_activity=0.1,
        embedding_ref="s3://sherlock-embeddings/m/p/9.npy",
    )
    out = sb.build_signals(det, captured_at_ms=3000)
    types = [s.type for s in out]
    assert sb.FACE_PRESENT in types
    assert sb.MULTIPLE_FACES in types
    assert len(out) == 2
