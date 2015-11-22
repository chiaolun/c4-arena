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

def standardize(state, side):
    N = len(state)
    standard_state = np.zeros((N,3))
    for i, x in enumerate(state):
        if x == 0:
            standard_state[i,0] = 1.
        elif x == side:
            standard_state[i,1] = 1.
        else:
            standard_state[i,2] = 1.
    return standard_state.reshape(1,state_dim*3)

def valid_columns(state):
    return [j for j in range(ncols)
            if any(state[0, 3 * i] == 1 for i in range(j * nrows, (j + 1) * nrows))]

class NeuralQ():
    def __init__(self, epsilon = 0.01, gamma = 1., save_interval = 100):
        self.epsilon = epsilon
        self.gamma = gamma
        self.save_interval = save_interval
        self.epoch = 0

        model = Sequential()
        model.add(Dense(30, init='lecun_uniform', input_shape=(state_dim*3,)))
        model.add(Activation('tanh'))
        #model.add(Dropout(0.2))

        model.add(Dense(15, init='lecun_uniform', input_shape=(state_dim,)))
        model.add(Activation('tanh'))
        #model.add(Dropout(0.2))

        model.add(Dense(ncols, init='lecun_uniform'))
        model.add(Activation('tanh')) #linear output so we can have range of real-valued outputs

        rms = RMSprop()
        model.compile(loss='mse', optimizer=rms)

        self.model = model

    def get_move(self, state, side):
        model = self.model
        gamma = self.gamma
        epsilon = self.epsilon

        state = standardize(state, side)
        state = np.array(state)
        #We are in state S
        #Let's run our Q function on S to get Q values for all possible actions
        qval = model.predict(state, batch_size=1)
        qval_allowed = np.empty(qval.shape)
        qval_allowed[:] = np.NAN
        valids = valid_columns(state)
        for i in valids:
            qval_allowed[0,i] = qval[0,i]

        if (random.random() < epsilon): #choose random action
            action = np.random.choice(valids)
        else: #choose best action from Q(s,a) values
            action = (np.nanargmax(qval_allowed))

        def observe_reward(reward=0, new_state=None):
            # This function observes the reward after the move chosen
            y = np.zeros((1,ncols))
            y[:] = qval[:]

            if not new_state:
                # Terminal state
                update = reward
            else:
                # Non-terminal state
                new_state = standardize(new_state, side)
                new_state = np.array(new_state)

                #Get max_Q(S',a)
                newQ = model.predict(new_state, batch_size=1)
                maxQ = np.max(newQ)

                update = (reward + (gamma * maxQ))

            y[0][action] = update #target output

            model.fit(state, y, batch_size=1, nb_epoch=1, verbose=1)
            self.epoch += 1
            if self.epoch % self.save_interval == 0:
                model.save_weights("model.dat", overwrite=True)

        return action, observe_reward
