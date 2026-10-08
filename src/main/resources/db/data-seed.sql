insert into topics (name, slug) values
    ('Lap trinh Java', 'lap-trinh-java'),
    ('Spring Boot', 'spring-boot'),
    ('CSDL', 'csdl'),
    ('Firebase', 'firebase'),
    ('UTE', 'ute')
on conflict (slug) do nothing;

insert into communities (owner_id, name, slug, description, is_private)
select u.id, 'ALOUTE Developers', 'aloute-developers', 'Cong dong trao doi lap trinh va xay dung ALOUTE.', false
from users u
where u.username = 'admin'
on conflict (slug) do nothing;

insert into community_members (community_id, user_id, role)
select c.id, u.id, 'ADMIN'
from communities c
join users u on u.id = c.owner_id
where c.slug = 'aloute-developers'
on conflict (community_id, user_id) do nothing;
