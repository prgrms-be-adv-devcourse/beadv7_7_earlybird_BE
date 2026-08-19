FROM docker.elastic.co/elasticsearch/elasticsearch:9.0.3

# 한국어 형태소 분석기 nori 설치 — 오늘의 모든 것이 이 한 줄에 달려 있습니다
RUN bin/elasticsearch-plugin install --batch analysis-nori

# nori 사용자 사전 + 동의어 사전 — project-index-settings.json의 user_dictionary/
# synonyms_path가 config 디렉터리 기준 상대경로로 이 파일들을 참조한다.
COPY analysis/userdict_ko.txt config/analysis/userdict_ko.txt
COPY analysis/synonym.txt config/analysis/synonym.txt
