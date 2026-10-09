#!/usr/bin/env python3
"""Static source consistency checks, not a substitute for Java compilation or Mongo integration tests."""
from pathlib import Path
import re

root=Path(__file__).resolve().parents[1]
src=root/'src/main/java/com/safayet/foodmobochain'
models=src/'model'
repos=src/'repository'
java=list(src.rglob('*.java'))
expected={'users','categories','foodCarts','foodItems','shoppingCarts','orders',
          'discounts','reviews','favoriteFoods','favoriteCarts','notifications','passwordResetTokens'}
collections=set()
for path in models.glob('*.java'):
    text=path.read_text()
    collections.update(re.findall(r'@Document\(collection\s*=\s*"([A-Za-z]+)"\)',text))
assert collections==expected, f'Unexpected root Mongo collections: {collections^expected}'
repository_files=list(repos.glob('*Repository.java'))
assert len(repository_files)==12, f'Expected 12 root repositories, got {len(repository_files)}'
for p in repository_files:
    assert 'extends MongoRepository<' in p.read_text(), f'Not a Mongo repository: {p}'
for p in java:
    s=p.read_text()
    assert not re.search(r'(?<!\w)(?:JpaRepository|EntityManager|jakarta\.persistence|javax\.persistence)(?!\w)',s), f'JPA found in {p}'
    if p.parent.name=='model':
        assert '@Entity' not in s, f'JPA entity in {p}'
assert 'spring-boot-starter-data-mongodb' in (root/'pom.xml').read_text()
assert 'spring-boot-starter-data-jpa' not in (root/'pom.xml').read_text()
assert 'mysql-connector' not in (root/'pom.xml').read_text()
assert 'spring.mongodb.uri=${MONGODB_URI}' in (root/'src/main/resources/application.properties').read_text()
for name in ['CartItem','OrderItem','Payment','Delivery']:
    assert '@Document' not in (models/f'{name}.java').read_text(), f'{name} must be embedded'
print(f'Static Mongo source checks passed: {len(java)} application Java files, {len(collections)} collections, {len(repository_files)} repositories')
print('These checks do NOT establish Java compilation or a working MongoDB application.')
