#!/usr/bin/env python

from keras.models import Sequential
from keras.layers.core import Dense, Dropout, Activation
from keras.optimizers import RMSprop
import numpy as np
import random

ncols = 7
nrows = 6
state_dim = ncols * nrows

# http://outlace.com/Reinforcement-Learning-Part-3/

class NeuralQ():
    def __init__(self, epsilon = 0.01, gamma = 1.):
        self.epsilon = epsilon
        self.gamma = gamma

        model = Sequential()
        model.add(Dense(15, init='lecun_uniform', input_shape=(state_dim,)))
        model.add(Activation('relu'))
        #model.add(Dropout(0.2)) I'm not using dropout, but maybe you wanna give it a try?

        model.add(Dense(ncols, init='lecun_uniform'))
        model.add(Activation('linear')) #linear output so we can have range of real-valued outputs

        rms = RMSprop()
        model.compile(loss='mse', optimizer=rms)

        self.model = model

    def get_move(self, state):
        state = np.array(state)
        self.state = state
        #We are in state S
        #Let's run our Q function on S to get Q values for all possible actions
        qval = model.predict(state.reshape(1,state_dim), batch_size=1)

        if (random.random() < self.epsilon): #choose random action
            action = np.random.randint(0, ncol)
        else: #choose best action from Q(s,a) values
            action = (np.argmax(qval))
        self.action = action
        return action

    def observe_reward(self, reward, state, action, new_state, terminal):
        new_state = np.array(new_state)

        qval = model.predict(state.reshape(1,state_dim), batch_size=1)

        #Get max_Q(S',a)
        newQ = model.predict(new_state.reshape(1,state_dim), batch_size=1)
        maxQ = np.max(newQ)

        y = np.zeros((1,ncols))
        y[:] = qval[:]

        if not terminal: #non-terminal state
            update = (reward + (self.gamma * maxQ))
        else:
            update = reward
        y[0][action] = update #target output

        model.fit(state.reshape(1,state_dim), y, batch_size=1, nb_epoch=1, verbose=1)
