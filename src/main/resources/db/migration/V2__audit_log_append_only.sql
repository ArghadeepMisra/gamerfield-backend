CREATE FUNCTION forbid_audit_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'moderation_actions is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_moderation_actions_append_only
    BEFORE UPDATE OR DELETE ON moderation_actions
    FOR EACH ROW EXECUTE FUNCTION forbid_audit_change();
