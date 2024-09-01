import pandas as pd
import numpy as np
from sklearn.metrics import accuracy_score
from eunjeon import Mecab
from tensorflow.keras.preprocessing.text import Tokenizer
from tensorflow.keras.preprocessing.sequence import pad_sequences
import re
from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import SimpleRNN, Embedding, Dense
from tensorflow.keras.optimizers import Adam
from sklearn.model_selection import train_test_split
import sklearn.metrics as metrics


def clean_str(text):
    if not isinstance(text, str):
        return ''

    pattern = '([a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\.[a-zA-Z0-9-.]+)' # E-mail제거
    text = re.sub(pattern=pattern, repl='', string=text)
    pattern = '(http|ftp|https)://(?:[-\w.]|(?:%[\da-fA-F]{2}))+' # URL제거
    text = re.sub(pattern=pattern, repl='', string=text)
    pattern = '([ㄱ-ㅎㅏ-ㅣ]+)'  # 한글 자음, 모음 제거
    text = re.sub(pattern=pattern, repl='', string=text)
    pattern = '<[^>]*>'         # HTML 태그 제거
    text = re.sub(pattern=pattern, repl='', string=text)
    pattern = '[^\w\s\n]'         # 특수기호제거
    text = re.sub(pattern=pattern, repl='', string=text)
    text = re.sub('[-=+,#/\?:^$.@*\"※~&%ㆍ!』\\‘|\(\)\[\]\<\>`\'…》_a-zA-Z0-9]','', string=text)
    text = re.sub('\n', '.', string=text)
    return text

try:
    # 스팸 데이터 가져오기
    df = pd.read_excel("C:/Users/cczzs/Desktop/KIS_KIS00000000000000022_20201123000000.xlsx")
    x_data = df['text']
    x_data = x_data.drop_duplicates()

    # 정상 데이터 가져오기 (개인 데이터)
    df2 = pd.read_excel("C:/Users/cczzs/Desktop/sms.xlsx")
    x2_data = df2['body']
    x2_data = x2_data.drop_duplicates()

    #print(x_data)
    #print(x2_data)

    # 데이터 전처리
    x_data = x_data.apply(clean_str)
    x2_data = x2_data.apply(clean_str);

    x_data = x_data.drop_duplicates()
    x2_data = x2_data.drop_duplicates()

    #print(x_data)
    #print(x2_data)

    tokenizer = Mecab()
    tokenizer_documents=[]
    for sentence in x_data:
        temp = tokenizer.morphs(sentence)  # 토큰화
        temp = [clean_str(word) for word in temp if len(word)>1]

        tokenizer_documents.append(temp)

    tokenizer2 = Mecab()
    tokenizer_documents2=[]
    for sentence in x2_data:
        temp = tokenizer2.morphs(sentence)  # 토큰화
        temp = [clean_str(word) for word in temp if len(word)>1]

        tokenizer_documents2.append(temp)

    #패딩 시퀀스의 MAX 값
    max_sequence_length = 20
    tokenizer = Tokenizer()

    # x_data에 대한 패딩
    tokenizer_documents_pad = []
    for sentences in tokenizer_documents:
        sequences = tokenizer.texts_to_sequences(sentences)
        padded_sequences = pad_sequences(sequences, maxlen=max_sequence_length, padding='post', truncating='post')
        tokenizer_documents_pad.extend(padded_sequences)

    # x2_data에 대한 패딩
    tokenizer_documents_pad2 = []
    for sentences in tokenizer_documents2:
        sequences = tokenizer.texts_to_sequences(sentences)
        padded_sequences = pad_sequences(sequences, maxlen=max_sequence_length, padding='post', truncating='post')
        tokenizer_documents_pad2.extend(padded_sequences)

    # 스팸 메시지와 정상 메시지의 개수 제한
    limit = 2000
    spam_messages = tokenizer_documents_pad[:limit]
    ham_messages = tokenizer_documents_pad2[:limit]

    # 레이블 생성
    spam_labels = [1] * len(spam_messages)
    ham_labels = [0] * len(ham_messages)

    # 학습 데이터와 레이블 결합
    X = np.concatenate((spam_messages, ham_messages), axis=0)
    y = np.array(spam_labels + ham_labels)

    # 학습 데이터 분할
    x_train, x_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=0)

    # 모델 정의 (RNN)
    model = Sequential()
    model.add(Embedding(input_dim=len(tokenizer.word_index) + 1, output_dim=32, input_length=max_sequence_length))
    model.add(SimpleRNN(32))
    model.add(Dense(1, activation='sigmoid'))

    # 모델 컴파일
    model.compile(loss='binary_crossentropy', optimizer=Adam(learning_rate=0.001), metrics=['accuracy'])

    # 모델 학습
    model.fit(x_train, y_train, epochs=10, batch_size=32, validation_split=0.2)

    # 모델 예측
    y_pred = model.predict(x_test)

    # 예측 결과를 이진 레이블로 변환
    threshold = 0.5
    binary_predictions = (y_pred > threshold).astype(int)

    # 성능 평가
    accuracy = accuracy_score(y_test, binary_predictions)
    print(f"정확성 : {accuracy}")
    precision = metrics.precision_score(y_test, binary_predictions)
    print(f"정밀도 : {precision}")
    recall = metrics.recall_score(y_test, binary_predictions)
    print(f"민감도 : {recall}")
    f1 = metrics.f1_score(y_test, binary_predictions)
    print(f"f1 점수 : {f1}")
    print("분류보고서:", metrics.classification_report(y_test, binary_predictions))

    # 실제 결과와 예측 결과 출력
    print("======================================")
    print("[실제 결과]")
    print(y_test[0:5])
    print("[예측 결과]")
    print((model.predict(x_test[0:5]) > threshold).astype(int))

    print("-------------------------------------------")

    # 테스트 텍스트
    test_text = "[부평구청] 4월 11일(일) 17시 기준 관내 확진환자 총 1명 발생(파악중 1명). 역학조사 진행중. 방역 완료. bit.ly/39SGMXI "

    # Mecab 객체 생성
    mecab = Mecab()

    # 텍스트 전처리
    cleaned_text = clean_str(test_text)

    # Mecab을 사용한 토큰화
    tokenized_text = mecab.morphs(cleaned_text)

    # 토큰화된 텍스트를 Keras Tokenizer 객체를 사용하여 정수 인코딩 및 패딩
    encoded_text = tokenizer.texts_to_sequences([tokenized_text])
    padded_text = pad_sequences(encoded_text, maxlen=max_sequence_length, padding='post', truncating='post')

    # 모델 예측
    prediction = model.predict(padded_text)
    binary_prediction = (prediction > threshold).astype(int)

    print("예측 결과:", binary_prediction)

    print("-------------------------------------------")

    # 테스트 텍스트
    test_text = "[Web발신] * 庚子年 * 새해에도건강과 幸福한삶소망하시는일모두 이루시길빕니다.서구의원 박양주 올림"

    # Mecab 객체 생성
    mecab = Mecab()

    # 텍스트 전처리
    cleaned_text = clean_str(test_text)

    # Mecab을 사용한 토큰화
    tokenized_text = mecab.morphs(cleaned_text)

    # 토큰화된 텍스트를 Keras Tokenizer 객체를 사용하여 정수 인코딩 및 패딩
    encoded_text = tokenizer.texts_to_sequences([tokenized_text])
    padded_text = pad_sequences(encoded_text, maxlen=max_sequence_length, padding='post', truncating='post')

    # 모델 예측
    prediction = model.predict(padded_text)
    binary_prediction = (prediction > threshold).astype(int)

    print("예측 결과:", binary_prediction)
    #model.save('my_saved_model')
except ValueError as e:
    print("ValueError 발생:", e)