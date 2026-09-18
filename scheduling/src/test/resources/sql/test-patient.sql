insert into patients (id, name, email, phone) overriding system value
values (900, 'Test Patient', 'test.patient@example.com', '+55 11 90000-0900')
    on conflict (id) do nothing;