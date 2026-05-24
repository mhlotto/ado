WITH home_project AS (
  SELECT id FROM projects WHERE core_key = 'home' AND deleted_at IS NULL
),
leaving_house_template AS (
  INSERT INTO task_templates (project_id, template_key, name, description, is_system)
  SELECT id, 'leaving_house', 'Leaving house', '', TRUE FROM home_project
  ON CONFLICT (template_key) DO UPDATE
    SET project_id = EXCLUDED.project_id, name = EXCLUDED.name, updated_at = now()
  RETURNING id
)
INSERT INTO task_template_items (template_id, name, description, position)
SELECT id, item_name, '', position
FROM leaving_house_template
CROSS JOIN (
  VALUES
    ('Lights off', 0),
    ('Small appliances unplugged', 1),
    ('Refrigerator / freezer doors shut', 2),
    ('Oven / stove off', 3),
    ('Doors locked', 4),
    ('Garage door closed', 5),
    ('Alarm set', 6)
) AS items(item_name, position)
WHERE NOT EXISTS (
  SELECT 1 FROM task_template_items WHERE template_id = leaving_house_template.id
);
